package com.roots.authserver.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.roots.authserver.integration.AuthServerClient.TokenResponse;

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
    }
}
