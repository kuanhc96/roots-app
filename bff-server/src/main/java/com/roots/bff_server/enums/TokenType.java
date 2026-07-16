package com.roots.bff_server.enums;

/**
 * The three tokens held per session in Redis, each under its own key
 * ({@code <sessionId>:<key>}) so each can carry its own TTL.
 */
public enum TokenType {
    ACCESS_TOKEN("access_token"),
    REFRESH_TOKEN("refresh_token"),
    ID_TOKEN("id_token");

    private final String key;

    TokenType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
