package com.roots.gateway_server.utility;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;

public class SessionIdExtractor {

    private static final String SESSION_COOKIE_NAME = "__Host-SESSION";

    private SessionIdExtractor() {
    }

    public static Optional<String> extractSessionId(ServerHttpRequest request) {
        HttpCookie sessionCookie = request.getCookies().getFirst(SESSION_COOKIE_NAME);
        if (sessionCookie == null || sessionCookie.getValue() == null || sessionCookie.getValue().isBlank()) {
            return Optional.empty();
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(sessionCookie.getValue());
            return Optional.of(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
