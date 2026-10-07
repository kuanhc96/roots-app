package com.roots.web_client_bff.service;

import com.roots.web_client_bff.client.SimpleResourceClient;
import com.roots.web_client_bff.dto.response.TokenResponse;
import com.roots.web_client_bff.enums.TokenType;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;

import lombok.RequiredArgsConstructor;

/**
 * Proxies role requests to simple-resource-server with the session's access token.
 * A missing access token is revived from the refresh token first; with neither, the
 * request is answered 401 without calling downstream.
 */
@Service
@RequiredArgsConstructor
public class RoleProxyService {

    private final TokenStoreService tokenStore;
    private final TokenRefreshService tokenRefreshService;
    private final SimpleResourceClient simpleResourceClient;

    public ResponseEntity<String> forward(String sessionId, String rolePath) {
        Optional<String> accessToken = tokenStore.find(sessionId, TokenType.ACCESS_TOKEN)
                .or(() -> tokenRefreshService.refresh(sessionId).map(TokenResponse::accessToken));
        if (accessToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return simpleResourceClient.getRole(rolePath, accessToken.get());
    }
}
