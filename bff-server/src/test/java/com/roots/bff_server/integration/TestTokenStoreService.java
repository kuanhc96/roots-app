package com.roots.bff_server.integration;

import com.roots.bff_server.enums.TokenType;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

/**
 * Test-side counterpart of the main {@code TokenStoreService}: reads and writes the
 * same {@code <sessionId>:<tokenName>} keys against the same Redis, but is connected
 * through the test properties ({@code redis-host}/{@code redis-port}) and extended
 * with what assertions need — TTL reads and bulk teardown. Defined as a bean in
 * {@link TestConfig}; the cached Spring test context shares one Lettuce connection
 * across the whole suite instead of opening a fresh one per test.
 */
@RequiredArgsConstructor
public class TestTokenStoreService {

    private final StringRedisTemplate redisTemplate;

    public Optional<String> find(String sessionId, TokenType type) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(sessionId, type)));
    }

    public void store(String sessionId, TokenType type, String value, Duration timeToLive) {
        redisTemplate.opsForValue().set(key(sessionId, type), value, timeToLive);
    }

    /** Remaining TTL in seconds ({@code -1} = key without expiry, {@code -2} = no such key). */
    public long getTimeToLive(String sessionId, TokenType type) {
        return redisTemplate.getExpire(key(sessionId, type));
    }

    /** Removes every per-session key (all {@link TokenType}s); idempotent, for teardown. */
    public void deleteAll(String sessionId) {
        for (TokenType type : TokenType.values()) {
            redisTemplate.delete(key(sessionId, type));
        }
    }

    private static String key(String sessionId, TokenType type) {
        return sessionId + ":" + type.key();
    }
}
