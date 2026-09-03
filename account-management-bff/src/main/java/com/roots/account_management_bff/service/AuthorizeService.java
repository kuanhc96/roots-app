package com.roots.account_management_bff.service;

import com.roots.account_management_bff.enums.TokenType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorizeService {

    private static final Duration STATE_TIME_TO_LIVE = Duration.ofMinutes(5);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TokenStoreService tokenStore;

    @Value("${auth-server.external-location}")
    private String authServerExternalLocation;
    @Value("${account-management.client.id}")
    private String clientId;
    @Value("${account-management-bff.external-location}")
    private String accountManagementBffExternalLocation;

    public URI buildAuthorizeRedirect(String sessionId) {
        String state = UUID.randomUUID().toString();
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String nonce = generateNonce();
        tokenStore.store(sessionId, TokenType.OAUTH_STATE, state, STATE_TIME_TO_LIVE);
        tokenStore.store(sessionId, TokenType.OAUTH_CODE_VERIFIER, codeVerifier, STATE_TIME_TO_LIVE);
        tokenStore.store(sessionId, TokenType.OAUTH_NONCE, nonce, STATE_TIME_TO_LIVE);

        return UriComponentsBuilder.fromUriString(authServerExternalLocation)
                .path("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", accountManagementBffExternalLocation + AuthCallbackService.CALLBACK_PATH)
                .queryParam("scope", "openid ACCOUNT_MANAGEMENT_CLIENT")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .encode()
                .build()
                .toUri();
    }

    private static String generateNonce() {
        return UUID.randomUUID().toString();
    }

    private static String generateCodeVerifier() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String generateCodeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
