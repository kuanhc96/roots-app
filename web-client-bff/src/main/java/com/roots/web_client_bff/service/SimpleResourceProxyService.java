package com.roots.web_client_bff.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class SimpleResourceProxyService {
    private final WebClient webClient;
    private final String simpleResourceServerLocation;

    public SimpleResourceProxyService(
            @Qualifier("simpleResourceServerWebClient") WebClient webClient,
            @Value("${simple-resource-server.location}") String simpleResourceServerLocation
    ) {
        this.webClient = webClient;
        this.simpleResourceServerLocation = removeTrailingSlash(simpleResourceServerLocation);
    }

    public Mono<ResponseEntity<String>> get(String endpointPath) {
        return webClient
                .get()
                .uri(simpleResourceServerLocation + endpointPath)
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
