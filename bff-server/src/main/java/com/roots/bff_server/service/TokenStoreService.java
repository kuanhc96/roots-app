package com.roots.bff_server.service;

import com.roots.bff_server.dto.response.TokenResponse;
import com.roots.bff_server.enums.TokenType;
import com.roots.bff_server.util.JwtPayload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

/**
 * Per-session token storage in Redis, owning both the keys and the lifetime policy.
 * Each token lives under its own {@code <sessionId>:<tokenName>} string key with its
 * own TTL, so Redis expires it exactly when the token itself does — an absent key
 * means an expired (or never issued) token, which is why reads never need an expiry
 * check.
 */
@Service
@RequiredArgsConstructor
public class TokenStoreService {

    private final StringRedisTemplate redisTemplate;

    // Field injection (not a constructor arg) so @RequiredArgsConstructor keeps wiring
    // the final dependencies — a generated constructor would drop the @Value annotation
    // and fail to bind (same pattern as auth-server's EmailService).
    // TODO: get this value from the DB
    @Value("${token-store.refresh-token-ttl-seconds}")
    private long refreshTokenTtlSeconds;

    public Optional<String> find(String sessionId, TokenType type) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(sessionId, type)));
    }

    /** Stores the token for the given TTL; a token already at/past expiry is not stored. */
    public void store(String sessionId, TokenType type, String token, Duration timeToLive) {
        if (timeToLive.isPositive()) {
            redisTemplate.opsForValue().set(key(sessionId, type), token, timeToLive);
        }
    }

    public void delete(String sessionId, TokenType type) {
        redisTemplate.delete(key(sessionId, type));
    }

    /**
     * Stores a full token-endpoint response for the session: JWTs (id/access) with
     * TTL = their own {@code exp}, and the rotated refresh token with the configured
     * TTL — or, in the unexpected case the response carries no refresh token, the
     * stored one is dropped (rotation invalidated the token that was just used).
     */
    public void storeTokenResponse(String sessionId, TokenResponse tokens) {
        storeJwt(sessionId, TokenType.ACCESS_TOKEN, tokens.accessToken());
        storeJwt(sessionId, TokenType.ID_TOKEN, tokens.idToken());

        if (tokens.refreshToken() != null) {
            store(sessionId, TokenType.REFRESH_TOKEN, tokens.refreshToken(),
                    Duration.ofSeconds(refreshTokenTtlSeconds));
        } else {
            delete(sessionId, TokenType.REFRESH_TOKEN);
        }
    }

    /** Stores a JWT with TTL = its own exp, so Redis drops it the moment it expires. */
    private void storeJwt(String sessionId, TokenType type, String jwt) {
        if (jwt == null) {
            return;
        }
        Duration timeToLive = Duration.between(Instant.now(), JwtPayload.parse(jwt).expiresAt());
        store(sessionId, type, jwt, timeToLive);
    }

    private static String key(String sessionId, TokenType type) {
        return sessionId + ":" + type.key();
    }
}
