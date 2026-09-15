package com.roots.account_management_client_bff.controller;

import com.roots.account_management_client_bff.dto.response.AccountProfileResponse;
import com.roots.account_management_client_bff.service.AccountManagementProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/account-management")
@RequiredArgsConstructor
public class AccountManagementProxyController {
    private static final String USER_GUID_CLAIM = "userGUID";

    private final AccountManagementProxyService accountManagementProxyService;

    @GetMapping("/profile")
    public Mono<ResponseEntity<AccountProfileResponse>> getAccountProfile(
            @RequestParam String userGUID,
            Authentication authentication
    ) {
        String authenticatedUserGuid = extractAuthenticatedUserGuid(authentication);
        if (authenticatedUserGuid == null || !authenticatedUserGuid.equals(userGUID)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }

        return accountManagementProxyService.getAccountProfile(userGUID);
    }

    private String extractAuthenticatedUserGuid(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token) || !oauth2Token.isAuthenticated()) {
            return null;
        }

        if (!(oauth2Token.getPrincipal() instanceof OidcUser oidcUser)) {
            return null;
        }

        return oidcUser.getClaimAsString(USER_GUID_CLAIM);
    }
}
