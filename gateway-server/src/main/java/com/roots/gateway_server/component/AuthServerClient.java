package com.roots.gateway_server.component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roots.gateway_server.dto.response.TokenExchangeResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AuthServerClient {

    private WebClient webClient;

    @PostConstruct
    private void setup() {
        webClient = WebClient.builder().baseUrl(authServerInternalLocation).build();
    }

    @Value("${auth-server.internal-location:http://localhost:9000}")
    private String authServerInternalLocation;

    // TODO: RCA-93: generalize for multiple clients/secrets
    @Value("${web.client.id:WEB_CLIENT}")
    private String webClientId;

    @Value("${web.client.secret:secret}")
    private String webClientSecret;

    public Mono<TokenExchangeResponse> exchangeRefreshToken(String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken);

        return webClient
                .post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth(webClientId, webClientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(AuthServerTokenResponse.class)
                .map(response -> new TokenExchangeResponse(
                        response.accessToken(),
                        response.expiresIn(),
                        response.refreshToken(),
                        response.idToken()
                ));
    }

    private record AuthServerTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("id_token") String idToken
    ) {
    }
}
