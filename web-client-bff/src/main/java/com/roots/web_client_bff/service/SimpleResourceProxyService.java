package com.roots.web_client_bff.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class SimpleResourceProxyService {
    private final WebClient webClient;
    private final String simpleResourceServerLocation;

    public SimpleResourceProxyService(
            WebClient.Builder webClientBuilder,
            @Value("${simple-resource-server.location}") String simpleResourceServerLocation
    ) {
        this.webClient = webClientBuilder.build();
        this.simpleResourceServerLocation = removeTrailingSlash(simpleResourceServerLocation);
    }

    public Mono<ResponseEntity<String>> get(String endpointPath, OAuth2AuthorizedClient authorizedClient) {
        return webClient
                .get()
                .uri(simpleResourceServerLocation + endpointPath)
                .headers(headers -> headers.setBearerAuth(authorizedClient.getAccessToken().getTokenValue()))
                .exchangeToMono(clientResponse -> clientResponse.toEntity(String.class))
                .map(response -> ResponseEntity.status(response.getStatusCode()).body(response.getBody()));
    }

    private String removeTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
