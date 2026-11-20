package com.roots.account_management_client_bff.service;

import com.roots.account_management_client_bff.dto.response.LoginStatusResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class AuthStatusService {

    public LoginStatusResponse getLoginStatus(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OidcUser user)) {
            return LoginStatusResponse.notLoggedIn();
        }
        return LoginStatusResponse.loggedIn(
                user.getEmail(), user.getClaimAsString("userGUID"), user.getClaimAsStringList("roles"));
    }
}
