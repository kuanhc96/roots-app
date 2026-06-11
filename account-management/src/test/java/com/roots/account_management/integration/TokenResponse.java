package com.roots.account_management.integration;

/**
 * Deserialized OAuth2 token endpoint response. Only the client_credentials grant is
 * used here, so refresh_token / id_token are always null.
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        String refreshToken,
        String idToken,
        String scope,
        String expiresIn
) {}
