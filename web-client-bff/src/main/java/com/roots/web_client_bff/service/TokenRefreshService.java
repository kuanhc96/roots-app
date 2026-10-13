package com.roots.web_client_bff.service;

import com.roots.web_client_bff.client.AuthServerTokenClient;
import com.roots.web_client_bff.dto.response.TokenResponse;
import com.roots.web_client_bff.enums.TokenType;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

import com.roots.web_client_bff.util.JwtPayload;
import lombok.RequiredArgsConstructor;

/**
 * Revives a session's tokens from its stored refresh token. On success the fresh
 * tokens are stored (rotation means the refresh token is always new) and returned;
 * on failure — or when no refresh token is held — the result is empty, and a
 * rejected refresh token is deleted since rotation means it can never succeed later.
 */
@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    private final TokenStoreService tokenStore;
    private final AuthServerTokenClient authServerTokenClient;

    public Optional<TokenResponse> refresh(String sessionId) {
        Optional<String> refreshToken = tokenStore.find(sessionId, TokenType.REFRESH_TOKEN);
        if (refreshToken.isEmpty()) {
            return Optional.empty();
        }

        Optional<TokenResponse> tokens = authServerTokenClient.refreshTokens(refreshToken.get());
        if (tokens.isEmpty()) {
            tokenStore.delete(sessionId, TokenType.REFRESH_TOKEN);
            return Optional.empty();
        }

        JwtPayload idTokenPayload = JwtPayload.parse(tokens.get().idToken());
        if (StringUtils.isBlank(idTokenPayload.getSid())) {
            tokenStore.delete(sessionId, TokenType.REFRESH_TOKEN);
            return Optional.empty();
        }

        tokenStore.storeTokenResponse(sessionId, tokens.get());
        return tokens;
    }
}
