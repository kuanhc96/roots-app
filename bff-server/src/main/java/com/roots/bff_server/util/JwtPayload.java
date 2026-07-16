package com.roots.bff_server.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Decoded JWT payload. No signature verification: every JWT the bff handles either
 * came straight from auth-server's token endpoint over a server-to-server call, or
 * out of Redis where the bff itself stored it.
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

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String name) {
        Object value = claims.get(name);
        return value == null ? null : (List<String>) value;
    }

    public Instant expiresAt() {
        return Instant.ofEpochSecond(((Number) claims.get("exp")).longValue());
    }
}
