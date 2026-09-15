package com.roots.account_management_client_bff.controller;

import com.roots.account_management_client_bff.dto.response.LoginStatusResponse;
import com.roots.account_management_client_bff.service.AuthStatusService;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthStatusService authStatusService;

    @Value("${auth-server.external-location:http://localhost:9000}")
    private String authServerExternalLocation;

    @Value("${account-management.client.id:ACCOUNT_MANAGEMENT_CLIENT}")
    private String clientId;

    @Value("${account-management-client-bff.external-location:http://localhost:8084}")
    private String postLogoutRedirectUri;

    @GetMapping("/status")
    public LoginStatusResponse getLoginStatus(Authentication authentication) {
        return authStatusService.getLoginStatus(authentication);
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

        return Mono.just(ResponseEntity.status(HttpStatus.FOUND).location(logoutRedirect).build());
//        return exchange.getSession()
//                .flatMap(session -> session.invalidate().thenReturn(logoutRedirect))
//                .switchIfEmpty(Mono.just(logoutRedirect))
//                .map(uri -> ResponseEntity.status(HttpStatus.FOUND).location(uri).build());
    }
}
