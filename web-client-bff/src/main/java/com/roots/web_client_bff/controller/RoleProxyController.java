package com.roots.web_client_bff.controller;

import com.roots.web_client_bff.service.SimpleResourceProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleProxyController {
    private final SimpleResourceProxyService simpleResourceProxyService;

    @GetMapping("/pastor")
    public Mono<ResponseEntity<String>> pastor(
            @RegisteredOAuth2AuthorizedClient("web-client-pkce-registration") OAuth2AuthorizedClient authorizedClient
    ) {
        return simpleResourceProxyService.get("/api/role/pastor", authorizedClient);
    }

    @GetMapping("/deacon")
    public Mono<ResponseEntity<String>> deacon(
            @RegisteredOAuth2AuthorizedClient("web-client-pkce-registration") OAuth2AuthorizedClient authorizedClient
    ) {
        return simpleResourceProxyService.get("/api/role/deacon", authorizedClient);
    }

    @GetMapping("/small-group-leader")
    public Mono<ResponseEntity<String>> smallGroupLeader(
            @RegisteredOAuth2AuthorizedClient("web-client-pkce-registration") OAuth2AuthorizedClient authorizedClient
    ) {
        return simpleResourceProxyService.get("/api/role/small-group-leader", authorizedClient);
    }

    @GetMapping("/vice-small-group-leader")
    public Mono<ResponseEntity<String>> viceSmallGroupLeader(
            @RegisteredOAuth2AuthorizedClient("web-client-pkce-registration") OAuth2AuthorizedClient authorizedClient
    ) {
        return simpleResourceProxyService.get("/api/role/vice-small-group-leader", authorizedClient);
    }

    @GetMapping("/member")
    public Mono<ResponseEntity<String>> member(
            @RegisteredOAuth2AuthorizedClient("web-client-pkce-registration") OAuth2AuthorizedClient authorizedClient
    ) {
        return simpleResourceProxyService.get("/api/role/member", authorizedClient);
    }

    @GetMapping("/guest")
    public Mono<ResponseEntity<String>> guest(
            @RegisteredOAuth2AuthorizedClient("web-client-pkce-registration") OAuth2AuthorizedClient authorizedClient
    ) {
        return simpleResourceProxyService.get("/api/role/guest", authorizedClient);
    }
}
