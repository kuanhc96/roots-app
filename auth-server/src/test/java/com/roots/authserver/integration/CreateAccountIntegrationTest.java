package com.roots.authserver.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({SpringExtension.class})
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class CreateAccountIntegrationTest {

    private static final String TEST_NAME = "Integration Test User";
    private static final String TEST_PASSWORD = "Password123";

    @Autowired
    private AuthServerClient client;

    @Autowired
    private OAuth2Client oAuth2Client;

    @Value("${auth-server-location}")
    private String authServerLocation;

    @Value("${web-client-location}")
    private String webClientLocation;

    @Value("${integration-test-client-secret}")
    private String integrationTestClientSecret;

    private String redirectUri;
    private String email;

    @BeforeEach
    void startOAuth2AuthorizationFlow() throws Exception {
        redirectUri = webClientLocation + "/callback";
        email = "itest+" + UUID.randomUUID() + "@example.com";

        // Start the authorization-code flow so a SavedRequest is held in the session;
        // magic-link verification redirects back to it (and thus to the callback).
        HttpResponse<String> authorizeResponse =
                client.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state");
        assertThat(authorizeResponse.statusCode()).isEqualTo(302);
        while (authorizeResponse.statusCode() == 302) {
            String location = authorizeResponse.headers().firstValue("Location").orElseThrow();
            if (location.startsWith(redirectUri)) {
                break;
            }
            authorizeResponse = client.getOnSession(HttpFlowUtils.resolveLocation(authServerLocation, location));
        }
    }

    @Test
    void createAccountFlow_verifiesEmailViaMagicLinkAndLandsOnCallback() throws Exception {
        // 1. Create the account with placeholder values.
        HttpResponse<String> createResponse = client.createAccount(TEST_NAME, email, TEST_PASSWORD);
        assertThat(createResponse.statusCode()).isEqualTo(201);

        // 2. Auto-login with the same credentials. The email is unverified, so we are
        //    redirected to the "check your email" page.
        HttpResponse<String> loginResponse = client.login(email, TEST_PASSWORD);
        assertThat(loginResponse.statusCode()).isEqualTo(302);
        assertThat(loginResponse.headers().firstValue("Location").orElseThrow()).endsWith("/signup/success");

        // 3. Client-credentials token exchange for the integration-test client.
        TokenResponse ccToken = oAuth2Client.getClientCredentialsToken(
                "INTEGRATION_TEST_CLIENT", integrationTestClientSecret, "INTEGRATION_TEST_CLIENT_WRITE");
        assertThat(ccToken.accessToken()).isNotBlank();

        // 4. Use the access token to mint the magic-link token for the new account.
        HttpResponse<String> magicLinkResponse = client.generateMagicLinkToken(ccToken.accessToken(), email);
        assertThat(magicLinkResponse.statusCode()).isEqualTo(200);
        String magicLinkToken = magicLinkResponse.body();
        assertThat(magicLinkToken).isNotBlank();

        // 5. Complete verification with the magic-link token, then follow the redirect
        //    chain; we should land on the web-client callback with an authorization code.
        HttpResponse<String> response = client.verifyMagicLink(magicLinkToken);
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
        assertThat(callback).contains("code=");
    }
}
