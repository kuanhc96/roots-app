package com.roots.account_management_client_bff.service;

import com.roots.account_management_client_bff.dto.response.TokenResponse;
import com.roots.account_management_client_bff.enums.TokenType;
import com.roots.account_management_client_bff.util.JwtPayload;
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

    @Value("${account-management.client.id}")
    private String clientId;

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
        if (tokens.idToken() != null) {
            JwtPayload idToken = JwtPayload.parse(tokens.idToken());
            Duration timeToLive = Duration.between(Instant.now(), idToken.expiresAt());
            if (timeToLive.isPositive()) {
                redisTemplate.opsForValue().set(sidKey(idToken.getSid()), sessionId, timeToLive);
            }
        }
        storeJwt(sessionId, TokenType.ACCESS_TOKEN, tokens.accessToken());
        storeJwt(sessionId, TokenType.ID_TOKEN, tokens.idToken());

        if (tokens.refreshToken() != null) {
            store(sessionId, TokenType.REFRESH_TOKEN, tokens.refreshToken(), Duration.ofSeconds(refreshTokenTtlSeconds));
        } else {
            delete(sessionId, TokenType.REFRESH_TOKEN);
        }
    }

    public void storeLogoutToken(String logoutToken) {
        JwtPayload payload = JwtPayload.parse(logoutToken);
        Duration timeToLive = Duration.between(Instant.now(), payload.expiresAt());
        if (timeToLive.isPositive()) {
            redisTemplate.opsForValue().set(logoutTokenKey(payload.getJti()), logoutToken, timeToLive);
        }
    }

    public boolean logoutTokenExists(String jti) {
        return org.springframework.util.StringUtils.hasText(
                redisTemplate.opsForValue().get(logoutTokenKey(jti)));
    }

    public Optional<String> findSessionIdBySid(String sid) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(sidKey(sid)));
    }

    public void clearTokensBySid(String sid) {
        findSessionIdBySid(sid).ifPresent(this::clearTokens);
        redisTemplate.delete(sidKey(sid));
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

    private String sidKey(String sid) {
        return "oidc:" + clientId + ":sid:" + sid;
    }

    private String logoutTokenKey(String jti) {
        return "oidc:" + clientId + ":logout_token:" + jti;
    }
}
