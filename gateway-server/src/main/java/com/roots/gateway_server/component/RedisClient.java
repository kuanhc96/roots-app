package com.roots.gateway_server.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import com.roots.gateway_server.dto.response.TokenExchangeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisClient {

    private static final String ACCESS_TOKEN_KEY_SUFFIX = ":access_token";
    private static final String REFRESH_TOKEN_KEY_SUFFIX = ":refresh_token";
    private static final String ID_TOKEN_KEY_SUFFIX = ":id_token";
    private static final String EXP_CLAIM = "exp";
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${token-store.refresh-token-ttl-seconds:3600}")
    private long refreshTokenTtlSeconds;

    public Optional<String> getAccessToken(String sessionId) {
        String redisKey = sessionId + ACCESS_TOKEN_KEY_SUFFIX;
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(redisKey));
    }

    public Optional<String> getRefreshToken(String sessionId) {
        String redisKey = sessionId + REFRESH_TOKEN_KEY_SUFFIX;
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(redisKey));
    }

    public void storeRefreshedTokens(String sessionId, TokenExchangeResponse tokens) {
        setValueWithTtl(sessionId + ACCESS_TOKEN_KEY_SUFFIX, tokens.accessToken(), tokens.accessTokenExpiresInSeconds());
        setValueWithTtl(sessionId + REFRESH_TOKEN_KEY_SUFFIX, tokens.refreshToken(), refreshTokenTtlSeconds);

        String idToken = tokens.idToken();
        if (idToken != null && !idToken.isBlank()) {
            long idTokenTtl = extractJwtExpSeconds(idToken).orElse(tokens.accessTokenExpiresInSeconds());
            setValueWithTtl(sessionId + ID_TOKEN_KEY_SUFFIX, idToken, idTokenTtl);
        }
    }

    private void setValueWithTtl(String key, String value, long ttlSeconds) {
        if (value == null || value.isBlank() || ttlSeconds <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    private Optional<Long> extractJwtExpSeconds(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            JsonNode payloadJson = objectMapper.readTree(payload);
            JsonNode expNode = payloadJson.get(EXP_CLAIM);
            if (expNode == null || !expNode.canConvertToLong()) {
                return Optional.empty();
            }
            long expEpochSeconds = expNode.longValue();
            long ttlSeconds = expEpochSeconds - Instant.now().getEpochSecond();
            return ttlSeconds > 0 ? Optional.of(ttlSeconds) : Optional.empty();
        } catch (IllegalArgumentException | IOException ignored) {
            return Optional.empty();
        }
    }
}
