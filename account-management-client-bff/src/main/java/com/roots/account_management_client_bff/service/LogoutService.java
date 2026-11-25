package com.roots.account_management_client_bff.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

@Service
public class LogoutService implements LogoutSuccessHandler {

    @Value("${auth-server.external-location}")
    private String authServerExternalLocation;
    @Value("${account-management.client.id}")
    private String clientId;
    @Value("${account-management.client.origin}")
    private String accountManagementClientOrigin;

    public URI buildLogoutRedirect(Authentication authentication) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authServerExternalLocation)
                .path("/connect/logout")
                .queryParam("client_id", clientId)
                .queryParam("post_logout_redirect_uri", accountManagementClientOrigin + "/logout");
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
            builder.queryParam("id_token_hint", user.getIdToken().getTokenValue());
        }
        return builder.encode().build().toUri();
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        response.sendRedirect(buildLogoutRedirect(authentication).toString());
    }
}
