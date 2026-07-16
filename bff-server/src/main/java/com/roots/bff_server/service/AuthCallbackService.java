package com.roots.bff_server.service;

import com.roots.bff_server.client.AuthServerTokenClient;
import com.roots.bff_server.dto.response.TokenResponse;
import com.roots.bff_server.enums.TokenType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

/**
 * Completes the authorization-code flow when auth-server redirects the browser back:
 * validates the returned {@code state} against the one minted at
 * {@code /api/auth/authorize} (strictly one-time — it is consumed on every callback
 * attempt, valid or not), exchanges the code server-side as WEB_CLIENT, stores the
 * token set in Redis under the session, and hands the browser to web-client. Any
 * failure — an {@code error} from auth-server, missing parameters, a state mismatch,
 * or a rejected exchange — lands on web-client with one generic error code; the
 * specific cause is only logged.
 */
@Service
@RequiredArgsConstructor
public class AuthCallbackService {

    /** Must match the {@code @GetMapping} of the callback in {@code AuthController}. */
    public static final String CALLBACK_PATH = "/api/auth/callback";

    private static final Logger log = LoggerFactory.getLogger(AuthCallbackService.class);
    private static final String FAILURE_CODE = "login_failed";

    private final TokenStoreService tokenStore;
    private final AuthServerTokenClient authServerTokenClient;

    // Field injection (not constructor args) so @RequiredArgsConstructor keeps wiring
    // the final dependencies (same pattern as the other services).
    @Value("${bff-server.external-location}")
    private String bffServerExternalLocation;
    @Value("${web.client.origin}")
    private String webClientOrigin;

    public URI handleCallback(String sessionId, String code, String state, String error) {
        // Consume the pending state unconditionally: one-time use, success or not.
        Optional<String> storedState = tokenStore.find(sessionId, TokenType.OAUTH_STATE);
        tokenStore.delete(sessionId, TokenType.OAUTH_STATE);

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

        Optional<TokenResponse> tokens = authServerTokenClient
                .exchangeAuthorizationCode(code, bffServerExternalLocation + CALLBACK_PATH)
                .filter(response -> response.idToken() != null && response.accessToken() != null);
        if (tokens.isEmpty()) {
            log.warn("Authorization-code exchange failed for session {}", sessionId);
            return failureRedirect();
        }

        tokenStore.storeTokenResponse(sessionId, tokens.get());
        return URI.create(webClientOrigin + "/");
    }

    private URI failureRedirect() {
        return URI.create(webClientOrigin + "/?e=" + FAILURE_CODE);
    }
}
