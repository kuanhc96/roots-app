package com.roots.web_client_bff.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class LoginController {
    @GetMapping("/login/oauth2/success")
    public Map<String, Object> loginSuccess(Authentication authentication) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("authenticated", authentication != null && authentication.isAuthenticated());

        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            response.put("registrationId", oauth2Token.getAuthorizedClientRegistrationId());
        }

        if (authentication != null) {
            response.put("name", authentication.getName());
            response.put(
                    "authorities",
                    authentication.getAuthorities().stream().map(Object::toString).toList()
            );
        } else {
            response.put("name", null);
            response.put("authorities", List.of());
        }

        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            response.put("claims", oidcUser.getClaims());
        }

        return response;
    }
}
