package com.roots.web_client_bff.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
public class LogoutService {
    @Value("${auth-server.external-location:${AUTH_SERVER_LOCATION:http://localhost:9000}}")
    private String authServerExternalLocation;

    @Value("${web.client.id:${WEB_CLIENT_ID:WEB_CLIENT_PKCE}}")
    private String clientId;

    @Value("${web.client.origin:${BASE_URL:http://localhost:8085}}")
    private String webClientOrigin;

    public URI buildLogoutRedirect(Authentication authentication) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authServerExternalLocation)
                .path("/connect/logout")
                .queryParam("client_id", clientId)
                .queryParam("post_logout_redirect_uri", webClientOrigin + "/logout");

        String idTokenHint = extractIdTokenHint(authentication);
        if (idTokenHint != null) {
            builder.queryParam("id_token_hint", idTokenHint);
        }

        return builder.encode().build().toUri();
    }

    private static String extractIdTokenHint(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            return null;
        }
        return oidcUser.getIdToken().getTokenValue();
    }
}
