package com.roots.bff_server.enums;

/**
 * The per-session values held in Redis, each under its own key
 * ({@code <sessionId>:<key>}) so each can carry its own TTL: the three OAuth2
 * tokens, plus the short-lived authorize-flow {@code state} and PKCE verifier.
 */
public enum TokenType {
    ACCESS_TOKEN("access_token"),
    REFRESH_TOKEN("refresh_token"),
    ID_TOKEN("id_token"),
    /** The OAuth2 state minted at /api/auth/authorize; validated by the future callback. */
    OAUTH_STATE("oauth_state"),
    /** The PKCE code_verifier minted at /api/auth/authorize; consumed at callback. */
    OAUTH_CODE_VERIFIER("oauth_code_verifier");

    private final String key;

    TokenType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
