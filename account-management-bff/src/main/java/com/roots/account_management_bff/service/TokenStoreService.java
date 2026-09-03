package com.roots.account_management_bff.service;

import com.roots.account_management_bff.dto.response.TokenResponse;
import com.roots.account_management_bff.enums.TokenType;
import com.roots.account_management_bff.util.JwtPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenStoreService {

    private final StringRedisTemplate redisTemplate;

    @Value("${token-store.refresh-token-ttl-seconds}")
    private long refreshTokenTtlSeconds;

    public Optional<String> find(String sessionId, TokenType type) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(sessionId, type)));
    }

    public void store(String sessionId, TokenType type, String token, Duration timeToLive) {
        if (timeToLive.isPositive()) {
            redisTemplate.opsForValue().set(key(sessionId, type), token, timeToLive);
        }
    }

    public void delete(String sessionId, TokenType type) {
        redisTemplate.delete(key(sessionId, type));
    }

    public void clearTokens(String sessionId) {
        delete(sessionId, TokenType.ACCESS_TOKEN);
        delete(sessionId, TokenType.ID_TOKEN);
        delete(sessionId, TokenType.REFRESH_TOKEN);
    }

    public void storeTokenResponse(String sessionId, TokenResponse tokens) {
        storeJwt(sessionId, TokenType.ACCESS_TOKEN, tokens.accessToken());
        storeJwt(sessionId, TokenType.ID_TOKEN, tokens.idToken());

        if (tokens.refreshToken() != null) {
            store(sessionId, TokenType.REFRESH_TOKEN, tokens.refreshToken(), Duration.ofSeconds(refreshTokenTtlSeconds));
        } else {
            delete(sessionId, TokenType.REFRESH_TOKEN);
        }
    }

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
