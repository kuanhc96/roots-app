package com.roots.bff_server.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Decoded JWT payload, without signature verification. Token-endpoint responses
 * and stored tokens are trusted; back-channel logout token validation is deferred.
 */
public record JwtPayload(Map<String, Object> claims) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** @throws IllegalArgumentException when the value is not a decodable JWT */
    public static JwtPayload parse(String jwt) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(jwt.split("\\.")[1]);
            return new JwtPayload(MAPPER.readValue(payload, new TypeReference<>() {
            }));
        } catch (Exception e) {
            throw new IllegalArgumentException("Not a decodable JWT", e);
        }
    }

    public String getString(String name) {
        Object value = claims.get(name);
        return value == null ? null : value.toString();
    }

    public String requireSid() {
        Object value = claims.get("sid");
        if (!(value instanceof String sid) || sid.isBlank()) {
            throw new IllegalArgumentException("JWT must contain a non-empty string sid");
        }
        return sid;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String name) {
        Object value = claims.get(name);
        return value == null ? null : (List<String>) value;
    }

    public Instant expiresAt() {
        return Instant.ofEpochSecond(((Number) claims.get("exp")).longValue());
    }
}
