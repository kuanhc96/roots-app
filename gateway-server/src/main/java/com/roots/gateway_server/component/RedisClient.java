package com.roots.gateway_server.component;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisClient {

    private static final String ACCESS_TOKEN_KEY_SUFFIX = ":access_token";
    private final StringRedisTemplate stringRedisTemplate;

    public Optional<String> getAccessToken(String sessionId) {
        String redisKey = sessionId + ACCESS_TOKEN_KEY_SUFFIX;
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(redisKey));
    }
}
