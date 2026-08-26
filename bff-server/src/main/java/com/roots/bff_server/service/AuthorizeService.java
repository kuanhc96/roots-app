package com.roots.bff_server.service;

import com.roots.bff_server.enums.TokenType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

/**
 * Kicks off the OAuth2 authorization-code flow on behalf of web-client: builds the
 * auth-server authorize URL the browser is redirected to, minting the {@code state}
 * and holding it in Redis ({@code <sessionId>:oauth_state}) so the bff — not the
 * browser — is the one that can validate it when the flow returns. Uses the
 * externally reachable auth-server base URL: the browser follows this redirect from
 * outside the docker network, where the internal hostname doesn't resolve.
 */
@Service
@RequiredArgsConstructor
public class AuthorizeService {

    /** Ample time to complete a login before the pending flow's state evaporates. */
    private static final Duration STATE_TIME_TO_LIVE = Duration.ofMinutes(5);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TokenStoreService tokenStore;

    // Field injection (not constructor args) so @RequiredArgsConstructor keeps wiring
    // the final dependencies (same pattern as AuthStatusService).
    @Value("${auth-server.external-location}")
    private String authServerExternalLocation;
    @Value("${web.client.id}")
    private String clientId;
    @Value("${bff-server.external-location}")
    private String bffServerExternalLocation;

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
                // The code comes back to the bff's own callback (a registered
                // redirect_uri), where the server-side exchange happens.
                .queryParam("redirect_uri", bffServerExternalLocation + AuthCallbackService.CALLBACK_PATH)
                .queryParam("scope", "openid WEB_CLIENT_READ")
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
