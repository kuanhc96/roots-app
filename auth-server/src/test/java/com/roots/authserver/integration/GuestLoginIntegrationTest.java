package com.roots.authserver.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.authserver.dto.TokenResponse;
import com.roots.authserver.util.HttpFlowUtils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GuestLoginIntegrationTest extends IntegrationTestBase {

    @Value("${web-client-location}")
    private String webClientLocation;

    @Value("${web-client-secret}")
    private String webClientSecret;

    @Test
    void guestLogin_shouldReturnAccessToken() throws Exception {
        String redirectUri = webClientLocation + "/callback";

        HttpResponse<String> authorizeResponse =
                authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state");
        assertThat(authorizeResponse.statusCode()).isEqualTo(302);
        authServerClient.getOnSession(HttpFlowUtils.resolveLocation(
                authServerLocation, authorizeResponse.headers().firstValue("Location").orElseThrow()));

        // Follow the redirect chain from the guest login until we land on the callback.
        HttpResponse<String> response = HttpFlowUtils.followRedirects(
                authServerClient, authServerLocation, authServerClient.loginAsGuest(), redirectUri);

        assertThat(response.statusCode()).isEqualTo(302);
        String callback = response.headers().firstValue("Location").orElseThrow();
        assertThat(callback).startsWith(redirectUri);
        String code = HttpFlowUtils.extractQueryParam(callback, "code");
        assertThat(code).isNotBlank();

        TokenResponse tokens = oAuth2Client.getAuthorizationGrantToken(code, "WEB_CLIENT", webClientSecret, redirectUri);
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.tokenType()).isEqualToIgnoringCase("Bearer");
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.idToken()).isNotBlank();

        byte[] payloadBytes = Base64.getUrlDecoder().decode(tokens.accessToken().split("\\.")[1]);
        Map<String, Object> claims = new ObjectMapper().readValue(payloadBytes, new TypeReference<>() {});
        List<String> roles = (List<String>) claims.get("roles");
        List<String> scopes = (List<String>) claims.get("scope");

        assertThat(roles.contains("GUEST"));
        assertThat(scopes.contains("openid"));
        assertThat(scopes.contains("WEB_CLIENT_READ"));
    }
}
