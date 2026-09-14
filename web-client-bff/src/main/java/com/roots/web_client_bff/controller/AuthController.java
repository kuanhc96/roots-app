package com.roots.web_client_bff.controller;

import com.roots.web_client_bff.dto.response.IdTokenClaimsResponse;
import com.roots.web_client_bff.dto.response.LoginStatusResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @Value("${auth-server.external-location:http://localhost:9000}")
    private String authServerExternalLocation;

    @Value("${web.client.id:WEB_CLIENT_PKCE}")
    private String clientId;

    @Value("${web.client.origin:${BASE_URL:http://localhost:8085}}")
    private String postLogoutRedirectUri;

    @GetMapping("/status")
    public LoginStatusResponse getLoginStatus(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token) || !oauth2Token.isAuthenticated()) {
            return LoginStatusResponse.notLoggedIn();
        }

        if (!(oauth2Token.getPrincipal() instanceof OidcUser oidcUser)) {
            return LoginStatusResponse.notLoggedIn();
        }

        return LoginStatusResponse.loggedIn(new IdTokenClaimsResponse(
                oidcUser.getEmail(),
                getStringClaim(oidcUser, "userGUID"),
                getStringListClaim(oidcUser, "roles")
        ));
    }

    @GetMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(Authentication authentication, ServerWebExchange exchange) {
        URI logoutRedirect = UriComponentsBuilder.fromUriString(authServerExternalLocation)
                .path("/connect/logout")
                .queryParam("client_id", clientId)
                .queryParam("post_logout_redirect_uri", postLogoutRedirectUri + "/logout")
                .queryParam("id_token_hint", authentication.getPrincipal() instanceof OidcUser oidcUser ? oidcUser.getIdToken().getTokenValue() : "")
                .build()
                .toUri();

        return exchange.getSession()
                .flatMap(session -> session.invalidate().thenReturn(logoutRedirect))
                .switchIfEmpty(Mono.just(logoutRedirect))
                .map(uri -> ResponseEntity.status(HttpStatus.FOUND).location(uri).build());
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
