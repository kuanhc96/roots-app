package com.roots.bff_server.client;

import com.roots.bff_server.dto.response.TokenResponse;
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

/**
 * Machine-to-machine client for auth-server's token endpoint. The bff authenticates
 * as WEB_CLIENT — refresh tokens are bound to the client they were issued to, so this
 * must be the same registered client web-client's authorization codes are issued for.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthServerTokenClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${web.client.id}")
    private String clientId;
    @Value("${web.client.secret}")
    private String clientSecret;
    @Value("${auth-server.internal-location}")
    private String authServerInternalLocation;

    private RestClient restClient;

    @PostConstruct
    public void setup() {
        restClient = restClientBuilder.baseUrl(authServerInternalLocation).build();
    }

    /**
     * Performs the refresh_token grant. Empty on any failure (an invalid, expired, or
     * already-rotated refresh token answers 400) — callers treat that as "no login".
     */
    public Optional<TokenResponse> refreshTokens(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);

        return exchange(form, "Refresh token");
    }

    /**
     * Performs the authorization_code grant. {@code redirectUri} must byte-for-byte
     * match the one sent on the authorize request (and a registered redirect_uri).
     * Empty on any failure (a bogus, expired, or already-used code answers 400).
     */
    public Optional<TokenResponse> exchangeAuthorizationCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

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
