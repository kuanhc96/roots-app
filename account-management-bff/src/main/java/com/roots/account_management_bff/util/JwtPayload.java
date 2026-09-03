package com.roots.account_management_bff.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public record JwtPayload(Map<String, Object> claims) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
