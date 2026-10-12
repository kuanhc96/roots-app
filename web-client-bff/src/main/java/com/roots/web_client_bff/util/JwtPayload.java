package com.roots.web_client_bff.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Decoded JWT claims and headers. No signature verification: callers are responsible
 * for establishing trust in the token.
 */
public record JwtPayload(Map<String, Object> claims, Map<String, Object> headers) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** @throws IllegalArgumentException when the value is not a decodable JWT */
    public static JwtPayload parse(String jwt) {
        try {
            String[] segments = jwt.split("\\.");
            byte[] header = Base64.getUrlDecoder().decode(segments[0]);
            byte[] payload = Base64.getUrlDecoder().decode(segments[1]);
            Map<String, Object> headers = MAPPER.readValue(header, new TypeReference<>() {
            });
            if (headers == null) {
                throw new IllegalArgumentException("JWT header must be a JSON object");
            }
            return new JwtPayload(MAPPER.readValue(payload, new TypeReference<>() {
            }), headers);
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
        Object value = claims.get("sid");
        if (!(value instanceof String sid) || sid.isBlank()) {
            return null;
        }
        return sid;
    }

    public String getAlg() {
        Object value = headers.get("alg");
        if (!(value instanceof String alg) || alg.isBlank()) {
            return null;
        }
        return alg;
    }

    public String getIss() {
        Object value = claims.get("iss");
        if (!(value instanceof String iss) || iss.isBlank()) {
            return null;
        }
        return iss;
    }

    public String getAud() {
        Object value = claims.get("aud");
        if (!(value instanceof String aud) || aud.isBlank()) {
            return null;
        }
        return aud;
    }

    public Instant getIat() {
        return Instant.ofEpochSecond(((Number) claims.get("iat")).longValue());
    }

    public String getNonce() {
        Object value = claims.get("nonce");
        if (!(value instanceof String nonce) || nonce.isBlank()) {
            return null;
        }
        return nonce;
    }

    public String getSub() {
        Object value = claims.get("sub");
        if (!(value instanceof String sub) || sub.isBlank()) {
            return null;
        }
        return sub;
    }
}
