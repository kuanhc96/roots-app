package com.roots.authserver.dto;

/**
 * Deserialized OAuth2 token endpoint response. Shared by the authorization_code and
 * client_credentials exchanges (refresh_token / id_token are null for the latter).
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        String refreshToken,
        String idToken,
        String scope,
        String expiresIn
) {}
