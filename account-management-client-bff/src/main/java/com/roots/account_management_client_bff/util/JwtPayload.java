package com.roots.account_management_client_bff.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public record JwtPayload(Map<String, Object> claims, Map<String, Object> headers) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JwtPayload parse(String jwt) {
        try {
            String[] segments = jwt.split("\\.");
            byte[] header = Base64.getUrlDecoder().decode(segments[0]);
            byte[] payload = Base64.getUrlDecoder().decode(segments[1]);
            return new JwtPayload(MAPPER.readValue(payload, new TypeReference<>() {
            }), MAPPER.readValue(header, new TypeReference<>() {
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

    public String getSid() {
        return getString("sid");
    }

    public String getAlg() {
        Object value = headers.get("alg");
        return value == null ? null : value.toString();
    }

    public String getIss() {
        return getString("iss");
    }

    public String getAud() {
        return getString("aud");
    }

    public Instant getIat() {
        return Instant.ofEpochSecond(((Number) claims.get("iat")).longValue());
    }

    public String getNonce() {
        return getString("nonce");
    }

    public String getSub() {
        return getString("sub");
    }

    public String getJti() {
        return getString("jti");
    }
}
