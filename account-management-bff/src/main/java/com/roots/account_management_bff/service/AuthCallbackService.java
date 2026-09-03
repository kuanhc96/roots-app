package com.roots.account_management_bff.service;

import com.roots.account_management_bff.client.AuthServerTokenClient;
import com.roots.account_management_bff.dto.response.TokenResponse;
import com.roots.account_management_bff.enums.TokenType;
import com.roots.account_management_bff.util.JwtPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthCallbackService {

    public static final String CALLBACK_PATH = "/api/auth/callback";

    private static final Logger log = LoggerFactory.getLogger(AuthCallbackService.class);
    private static final String FAILURE_CODE = "login_failed";

    private final TokenStoreService tokenStore;
    private final AuthServerTokenClient authServerTokenClient;

    @Value("${account-management-bff.external-location}")
    private String accountManagementBffExternalLocation;
    @Value("${account-management.client.origin}")
    private String accountManagementClientOrigin;

    public URI handleCallback(String sessionId, String code, String state, String error) {
        Optional<String> storedState = tokenStore.find(sessionId, TokenType.OAUTH_STATE);
        Optional<String> storedCodeVerifier = tokenStore.find(sessionId, TokenType.OAUTH_CODE_VERIFIER);
        Optional<String> storedNonce = tokenStore.find(sessionId, TokenType.OAUTH_NONCE);
        tokenStore.delete(sessionId, TokenType.OAUTH_STATE);
        tokenStore.delete(sessionId, TokenType.OAUTH_CODE_VERIFIER);
        tokenStore.delete(sessionId, TokenType.OAUTH_NONCE);

        if (error != null) {
            log.warn("Authorization failed at auth-server for session {}: {}", sessionId, error);
            return failureRedirect();
        }
        if (code == null || state == null) {
            log.warn("Callback for session {} is missing code or state", sessionId);
            return failureRedirect();
        }
        if (storedState.isEmpty() || !storedState.get().equals(state)) {
            log.warn("State mismatch on callback for session {} — possible CSRF or expired flow", sessionId);
            return failureRedirect();
        }
        if (storedCodeVerifier.isEmpty()) {
            log.warn("Missing PKCE code_verifier on callback for session {}", sessionId);
            return failureRedirect();
        }

        Optional<TokenResponse> tokens = authServerTokenClient
                .exchangeAuthorizationCode(code, accountManagementBffExternalLocation + CALLBACK_PATH, storedCodeVerifier.get())
                .filter(response -> response.idToken() != null && response.accessToken() != null);
        if (tokens.isEmpty()) {
            log.warn("Authorization-code exchange failed for session {}", sessionId);
            return failureRedirect();
        }

        String idTokenNonce = JwtPayload.parse(tokens.get().idToken()).getString("nonce");
        if (storedNonce.isEmpty() || !storedNonce.get().equals(idTokenNonce)) {
            log.warn("Nonce mismatch on callback for session {} — id_token nonce does not match stored nonce", sessionId);
            return failureRedirect();
        }

        tokenStore.storeTokenResponse(sessionId, tokens.get());
        return URI.create(accountManagementClientOrigin + "/");
    }

    private URI failureRedirect() {
        return URI.create(accountManagementClientOrigin + "/?e=" + FAILURE_CODE);
    }
}
