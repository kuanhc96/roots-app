package com.roots.web_client_bff.client;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;

/**
 * Server-to-server client for simple-resource-server, reached through gateway-server's
 * {@code /roots-app/simple-resource-server} route. The bff attaches the session's access
 * token itself and sends no session cookie, so the gateway's cookie-driven token filters
 * are no-ops and the bearer header passes through untouched.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SimpleResourceClient {

    private static final String ROUTE_PREFIX = "/roots-app/simple-resource-server";

    private final RestClient.Builder restClientBuilder;

    @Value("${gateway-server.internal-location}")
    private String gatewayServerInternalLocation;

    private RestClient restClient;

    @PostConstruct
    public void setup() {
        restClient = restClientBuilder.baseUrl(gatewayServerInternalLocation + ROUTE_PREFIX).build();
    }

    /**
     * GETs {@code /role/<rolePath>} with the given bearer token. 2xx/4xx responses are
     * relayed verbatim (status + text body); a 5xx or a connection failure becomes 502.
     */
    public ResponseEntity<String> getRole(String rolePath, String accessToken) {
        try {
            return restClient.get()
                    .uri("/role/{rolePath}", rolePath)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .exchange((request, response) -> {
                        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
                        if (status == null || status.is5xxServerError()) {
                            log.warn("simple-resource-server /role/{} answered {}", rolePath, response.getStatusCode());
                            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
                        }
                        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        return ResponseEntity.status(status)
                                .contentType(MediaType.TEXT_PLAIN)
                                .body(body);
                    });
        } catch (RestClientException e) {
            log.warn("Unable to reach gateway-server for /role/{}: {}", rolePath, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
