package com.roots.gateway_server.dto.response;

public record TokenExchangeResponse(
        String accessToken,
        long accessTokenExpiresInSeconds,
        String refreshToken,
        String idToken
) {
}
