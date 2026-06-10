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

import java.net.http.HttpResponse;
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

    @Autowired
    private OAuth2Client oAuth2Client;

    @Value("${auth-server-location}")
    private String authServerLocation;

    @Value("${web-client-location}")
    private String webClientLocation;

    @Value("${web-client-secret}")
    private String webClientSecret;

    @Test
    void guestLogin_shouldReturnAccessToken() throws Exception {
        String redirectUri = webClientLocation + "/callback";

        client.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state");

        // Follow the redirect chain from the guest login until we land on the callback.
        HttpResponse<String> response = client.loginAsGuest();
        while (response.statusCode() == 302) {
            String location = response.headers().firstValue("Location").orElseThrow();
            if (location.startsWith(redirectUri)) {
                break;
            }
            response = client.getOnSession(HttpFlowUtils.resolveLocation(authServerLocation, location));
        }

        assertThat(response.statusCode()).isEqualTo(302);
        String callback = response.headers().firstValue("Location").orElseThrow();
        assertThat(callback).startsWith(redirectUri);
        String code = HttpFlowUtils.extractQueryParam(callback, "code");
        assertThat(code).isNotBlank();

        TokenResponse tokens = oAuth2Client.exchangeCodeForToken(code, "WEB_CLIENT", webClientSecret, redirectUri);
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
