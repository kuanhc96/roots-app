package com.roots.authserver.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateAccountIntegrationTest extends IntegrationTestBase {

    private static final String TEST_NAME = "Integration Test User";
    private static final String TEST_PASSWORD = "Password123";

    @Value("${web-client-location}")
    private String webClientLocation;

    private String redirectUri;
    private String email;

    @BeforeEach
    void startOAuth2AuthorizationFlow() throws Exception {
        redirectUri = webClientLocation + "/callback";
        email = "itest_" + UUID.randomUUID() + "@example.com";

        // Start the authorization-code flow so a SavedRequest is held in the session;
        // magic-link verification redirects back to it (and thus to the callback).
        HttpResponse<String> authorizeResponse =
                authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state");
        assertThat(authorizeResponse.statusCode()).isEqualTo(302);
        HttpFlowUtils.followRedirects(authServerClient, authServerLocation, authorizeResponse, redirectUri);
    }

    @Test
    void createAccountFlow_verifiesEmailViaMagicLinkAndLandsOnCallback() throws Exception {
        // 1. Create the account with placeholder values.
        HttpResponse<String> createResponse = authServerClient.createAccount(TEST_NAME, email, TEST_PASSWORD);
        assertThat(createResponse.statusCode()).isEqualTo(201);
        UserCredentialTestingResponse testAccount =  accountManagementClient.getTestAccountByEmail(email).getBody();
        assertThat(testAccount).isNotNull();
        assertThat(testAccount.emailVerified()).isFalse();

        // 2. Auto-login with the same credentials. The email is unverified, so we are
        //    redirected to the "check your email" page.
        HttpResponse<String> loginResponse = authServerClient.login(email, TEST_PASSWORD);
        assertThat(loginResponse.statusCode()).isEqualTo(302);
        assertThat(loginResponse.headers().firstValue("Location").orElseThrow()).endsWith("/signup/success");

        // 4. Use the access token to mint the magic-link token for the new account.
        HttpResponse<String> magicLinkResponse = authServerClient.generateMagicLinkToken(email);
        assertThat(magicLinkResponse.statusCode()).isEqualTo(200);
        String magicLinkToken = magicLinkResponse.body();
        assertThat(magicLinkToken).isNotBlank();

        // 5. Complete verification with the magic-link token, then follow the redirect
        //    chain; we should land on the web-client callback with an authorization code.
        HttpResponse<String> response = HttpFlowUtils.followRedirects(
                authServerClient, authServerLocation, authServerClient.verifyMagicLink(magicLinkToken), redirectUri);

        assertThat(response.statusCode()).isEqualTo(302);
        String callback = response.headers().firstValue("Location").orElseThrow();
        assertThat(callback).startsWith(redirectUri);
        assertThat(callback).contains("code=");

        testAccount =  accountManagementClient.getTestAccountByEmail(email).getBody();
        assertThat(testAccount).isNotNull();
        assertThat(testAccount.emailVerified()).isTrue();
    }
}
