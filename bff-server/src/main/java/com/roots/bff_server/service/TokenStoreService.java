package com.roots.bff_server.service;

import com.roots.bff_server.dto.response.TokenResponse;
import com.roots.bff_server.enums.TokenType;
import com.roots.bff_server.util.JwtPayload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(TokenStoreService.class);

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
     * Removes the session's three OAuth2 tokens and its known sid mapping — the counterpart to
     * {@link #storeTokenResponse}. Used at logout: once these are gone the session is
     * no longer a login (an absent id_token/refresh token is exactly what
     * {@code AuthStatusService} treats as "not logged in"). The short-lived
     * {@code oauth_state} is left alone — it only exists mid-authorize-flow.
     */
    public void clearTokens(String sessionId) {
        Optional<String> idToken = find(sessionId, TokenType.ID_TOKEN);
        delete(sessionId, TokenType.ACCESS_TOKEN);
        delete(sessionId, TokenType.ID_TOKEN);
        delete(sessionId, TokenType.REFRESH_TOKEN);
        removeKnownSidMapping(sessionId, idToken);
    }

    /**
     * Stores a full token-endpoint response for the session: JWTs (id/access) with
     * TTL = their own {@code exp}, and the rotated refresh token with the configured
     * TTL — or, in the unexpected case the response carries no refresh token, the
     * stored one is dropped (rotation invalidated the token that was just used).
     * The ID token must contain a valid sid; its session mapping uses the configured
     * refresh-token TTL, renewed on every successful BFF refresh.
     */
    public void storeTokenResponse(String sessionId, TokenResponse tokens) {
        JwtPayload idToken = JwtPayload.parse(tokens.idToken());
        String sid = idToken.requireSid();
        Duration idTokenTtl = Duration.between(Instant.now(), idToken.expiresAt());
        removeKnownSidMapping(sessionId, find(sessionId, TokenType.ID_TOKEN));
        storeJwt(sessionId, TokenType.ACCESS_TOKEN, tokens.accessToken());
        store(sessionId, TokenType.ID_TOKEN, tokens.idToken(), idTokenTtl);

        if (tokens.refreshToken() != null) {
            store(sessionId, TokenType.REFRESH_TOKEN, tokens.refreshToken(),
                    Duration.ofSeconds(refreshTokenTtlSeconds));
        } else {
            delete(sessionId, TokenType.REFRESH_TOKEN);
        }

        Duration mappingTtl = Duration.ofSeconds(refreshTokenTtlSeconds);
        if (mappingTtl.isPositive()) {
            redisTemplate.opsForValue().set(sidKey(sid), sessionId, mappingTtl);
        }
    }

    public Optional<String> findSessionIdBySid(String sid) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(sidKey(sid)));
    }

    public void clearTokensBySid(String sid) {
        findSessionIdBySid(sid).ifPresent(this::clearTokens);
        redisTemplate.delete(sidKey(sid));
    }

    private void removeKnownSidMapping(String sessionId, Optional<String> idToken) {
        if (idToken.isEmpty()) {
            return;
        }
        String sid;
        try {
            sid = JwtPayload.parse(idToken.get()).requireSid();
        } catch (IllegalArgumentException e) {
            log.warn("Cannot clean sid mapping for session {}: stored id_token has no decodable sid", sessionId);
            return;
        }
        if (findSessionIdBySid(sid).filter(sessionId::equals).isPresent()) {
            redisTemplate.delete(sidKey(sid));
        }
    }

    private static String sidKey(String sid) {
        return "oidc:sid:" + sid;
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
