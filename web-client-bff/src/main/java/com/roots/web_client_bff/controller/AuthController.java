package com.roots.web_client_bff.controller;

import com.roots.web_client_bff.dto.response.LoginStatusResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @GetMapping("/status")
    public LoginStatusResponse getLoginStatus(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OidcUser user)) {
            return LoginStatusResponse.notLoggedIn();
        }
        return LoginStatusResponse.loggedIn(user.getEmail(), user.getClaimAsString("userGUID"),
                user.getClaimAsStringList("roles"));
    }
}
