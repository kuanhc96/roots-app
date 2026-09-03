package com.roots.account_management_bff.client;

import com.roots.account_management_bff.dto.response.TokenResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuthServerTokenClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${account-management.client.id}")
    private String clientId;
    @Value("${account-management.client.secret}")
    private String clientSecret;
    @Value("${auth-server.internal-location}")
    private String authServerInternalLocation;

    private RestClient restClient;

    @PostConstruct
    public void setup() {
        restClient = restClientBuilder.baseUrl(authServerInternalLocation).build();
    }

    public Optional<TokenResponse> refreshTokens(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);

        return exchange(form, "Refresh token");
    }

    public Optional<TokenResponse> exchangeAuthorizationCode(String code, String redirectUri, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", codeVerifier);
        form.add("client_id", clientId);

        return exchange(form, "Authorization code");
    }

    private Optional<TokenResponse> exchange(MultiValueMap<String, String> form, String grantLabel) {
        try {
            return Optional.ofNullable(restClient.post()
                    .uri("/oauth2/token")
                    .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class));
        } catch (RestClientException e) {
            log.warn("{} exchange failed: {}", grantLabel, e.getMessage());
            return Optional.empty();
        }
    }
}
