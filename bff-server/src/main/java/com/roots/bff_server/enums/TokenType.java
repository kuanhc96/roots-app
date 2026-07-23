package com.roots.bff_server.enums;

/**
 * The per-session values held in Redis, each under its own key
 * ({@code <sessionId>:<key>}) so each can carry its own TTL: the three OAuth2
 * tokens, plus the short-lived authorize-flow {@code state}.
 */
public enum TokenType {
    ACCESS_TOKEN("access_token"),
    REFRESH_TOKEN("refresh_token"),
    ID_TOKEN("id_token"),
    /** The OAuth2 state minted at /api/auth/authorize; validated by the future callback. */
    OAUTH_STATE("oauth_state");

    private final String key;

    TokenType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
