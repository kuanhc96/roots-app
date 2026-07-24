package com.roots.authserver.service;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;

/**
 * Kicks off and completes the Google OAuth2 authorization-code flow's CSRF handshake.
 * The {@code state} is minted here and held in Redis keyed by the caller's HttpSession
 * id (mirroring bff-server's TokenStoreService pattern) so the backend — not the
 * browser — is the one that validates it when Google redirects back to
 * {@code /login/google/callback}.
 */
@Service
@RequiredArgsConstructor
public class GoogleAuthorizeService {

    private static final String GOOGLE_AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";

    /** Ample time to complete a login before the pending flow's state evaporates. */
    private static final Duration STATE_TIME_TO_LIVE = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    // Field injection (not a constructor arg) so @RequiredArgsConstructor keeps wiring
    // the final dependencies (same pattern as bff-server's AuthorizeService).
    @Value("${google.client-id}")
    private String googleClientId;

    public URI buildAuthorizeRedirect(String sessionId, String redirectUri) {
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(stateKey(sessionId), state, STATE_TIME_TO_LIVE);

        return UriComponentsBuilder.fromUriString(GOOGLE_AUTHORIZATION_ENDPOINT)
                .queryParam("response_type", "code")
                .queryParam("client_id", googleClientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();
    }

    /**
     * Validates the returned state against the one minted for this session. Consumes
     * it unconditionally (deletes from Redis) — single-use, success or not — so a
     * replayed callback can never succeed twice.
     */
    public boolean consumeAndValidateState(String sessionId, String returnedState) {
        String key = stateKey(sessionId);
        Optional<String> storedState = Optional.ofNullable(redisTemplate.opsForValue().get(key));
        redisTemplate.delete(key);
        return returnedState != null && storedState.isPresent() && storedState.get().equals(returnedState);
    }

    private static String stateKey(String sessionId) {
        return sessionId + ":oauth_state";
    }
}
