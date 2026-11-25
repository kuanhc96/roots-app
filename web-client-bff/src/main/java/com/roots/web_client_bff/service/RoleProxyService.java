package com.roots.web_client_bff.service;

import com.roots.web_client_bff.client.SimpleResourceClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RoleProxyService {
    private static final String REGISTRATION_ID = "web-client-registration";
    private final OAuth2AuthorizedClientManager authorizedClients;
    private final SimpleResourceClient simpleResourceClient;

    public ResponseEntity<String> forward(Authentication authentication, HttpServletRequest request,
                                          HttpServletResponse response, String rolePath) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2) || !oauth2.isAuthenticated()
                || !REGISTRATION_ID.equals(oauth2.getAuthorizedClientRegistrationId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
                .principal(authentication)
                .attribute(HttpServletRequest.class.getName(), request)
                .attribute(HttpServletResponse.class.getName(), response)
                .build();
        OAuth2AuthorizedClient client;
        try {
            client = authorizedClients.authorize(authorizeRequest);
        } catch (OAuth2AuthorizationException ex) {
            if ("invalid_grant".equals(ex.getError().getErrorCode())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            throw ex;
        }
        if (client == null || client.getAccessToken() == null
                || (client.getAccessToken().getExpiresAt() != null
                    && !client.getAccessToken().getExpiresAt().isAfter(Instant.now()))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return simpleResourceClient.getRole(rolePath, client.getAccessToken().getTokenValue());
    }
}
