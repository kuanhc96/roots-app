package com.roots.authserver.dto.request;

import java.util.Map;

import lombok.Builder;

@Builder
public record LogoutToken(
        String iss,
        String sub,
        String aud,
        String sid,
        String jti,
        Long iat,
        Map<String, Object> events
) {
}
