package com.roots.account_management_client_bff.service;

import com.roots.account_management_client_bff.dto.response.IdTokenClaimsResponse;
import com.roots.account_management_client_bff.dto.response.LoginStatusResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthStatusService {

    public LoginStatusResponse getLoginStatus(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token) || !oauth2Token.isAuthenticated()) {
            return LoginStatusResponse.notLoggedIn();
        }

        if (!(oauth2Token.getPrincipal() instanceof OidcUser oidcUser)) {
            return LoginStatusResponse.notLoggedIn();
        }

        return LoginStatusResponse.loggedIn(
                new IdTokenClaimsResponse(
                        oidcUser.getEmail(),
                        getStringClaim(oidcUser, "userGUID"),
                        getStringListClaim(oidcUser, "roles")));
    }

    private static String getStringClaim(OidcUser oidcUser, String claimName) {
        Object value = oidcUser.getClaims().get(claimName);
        return value == null ? null : value.toString();
    }

    private static List<String> getStringListClaim(OidcUser oidcUser, String claimName) {
        List<String> values = oidcUser.getClaimAsStringList(claimName);
        return values == null ? List.of() : values;
    }
}
