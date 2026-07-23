package com.roots.bff_server.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Anchors the integration tests' Spring context so {@code @TestPropertySource}/
 * {@code @Value} can resolve the connection settings (same pattern as auth-server's
 * integration suite), and provides the Redis-backed {@link TestTokenStoreService}
 * the tests use to seed and assert token/state keys. The connection factory's
 * lifecycle (start/stop) is managed by the cached test context — one Lettuce
 * connection for the whole suite. (The per-test-fresh rule applies to the HTTP
 * clients, whose pooled keep-alive connections the server reaps; Lettuce reconnects
 * itself and Redis doesn't drop idle connections by default.)
 */
@Configuration
public class TestConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${redis-host}") String redisHost,
            @Value("${redis-port}") int redisPort) {
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    public TestTokenStoreService testTokenStoreService(StringRedisTemplate stringRedisTemplate) {
        return new TestTokenStoreService(stringRedisTemplate);
    }
}
