package com.roots.account_management_bff.service;

import com.roots.account_management_bff.enums.TokenType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LogoutService {

    private final TokenStoreService tokenStore;

    @Value("${auth-server.external-location}")
    private String authServerExternalLocation;
    @Value("${account-management.client.id}")
    private String clientId;
    @Value("${account-management.client.origin}")
    private String accountManagementClientOrigin;

    public URI buildLogoutRedirect(String sessionId) {
        Optional<String> idToken = tokenStore.find(sessionId, TokenType.ID_TOKEN);
        tokenStore.clearTokens(sessionId);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authServerExternalLocation)
                .path("/connect/logout")
                .queryParam("client_id", clientId)
                .queryParam("post_logout_redirect_uri", accountManagementClientOrigin + "/logout");
        idToken.ifPresent(token -> builder.queryParam("id_token_hint", token));

        return builder.encode().build().toUri();
    }

}
