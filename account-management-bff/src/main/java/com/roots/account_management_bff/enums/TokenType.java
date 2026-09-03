package com.roots.account_management_bff.enums;

public enum TokenType {
    ACCESS_TOKEN("access_token"),
    REFRESH_TOKEN("refresh_token"),
    ID_TOKEN("id_token"),
    OAUTH_STATE("oauth_state"),
    OAUTH_CODE_VERIFIER("oauth_code_verifier"),
    OAUTH_NONCE("oauth_nonce");

    private final String key;

    TokenType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
