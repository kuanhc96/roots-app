package com.roots.bff_server.service;

import com.roots.bff_server.enums.TokenType;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

/**
 * Per-session token storage in Redis. Each token lives under its own
 * {@code <sessionId>:<tokenName>} string key with its own TTL, so Redis expires it
 * exactly when the token itself does — an absent key means an expired (or never
 * issued) token, which is why reads never need an expiry check.
 */
@Service
@RequiredArgsConstructor
public class TokenStoreService {

    private final StringRedisTemplate redisTemplate;

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

    private static String key(String sessionId, TokenType type) {
        return sessionId + ":" + type.key();
    }
}
