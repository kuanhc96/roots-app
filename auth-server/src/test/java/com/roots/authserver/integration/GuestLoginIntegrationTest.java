package com.roots.authserver.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.roots.authserver.integration.AuthServerClient.TokenResponse;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({SpringExtension.class})
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class GuestLoginIntegrationTest {

    @Autowired
    private AuthServerClient client;

    @Value("${web-client-location}")
    private String webClientLocation;

    @Value("${web-client-secret}")
    private String webClientSecret;

    @Test
    void guestLogin_shouldReturnAccessToken() throws Exception {
        String redirectUri = webClientLocation + "/callback";

        client.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state");

        String code = client.loginAsGuest(redirectUri);
        assertThat(code).isNotBlank();

        TokenResponse tokens = client.exchangeCodeForToken(code, "WEB_CLIENT", webClientSecret, redirectUri);
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
