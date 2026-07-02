package com.roots.authserver.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.authserver.dto.CreateTestAccountResponse;
import com.roots.authserver.dto.TokenResponse;
import com.roots.authserver.util.HttpFlowUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the form-login flow end-to-end against a live auth-server and
 * account-management. There is deliberately no account-creating @BeforeEach: each test
 * creates the account variant it needs (recording the userGUID for cleanup), so tests
 * with different flag combinations (MFA on/off, verified or not) can share the class.
 */
class LoginIntegrationTest extends IntegrationTestBase {

    private static final String TEST_NAME = "Integration Test User";
    private static final String TEST_PASSWORD = "Password123";

    @Value("${web-client-location}")
    private String webClientLocation;

    @Value("${web-client-secret}")
    private String webClientSecret;

    private String email;
    private String userGUID;

    @AfterEach
    void deleteTestAccount() {
        // Tests create their own account; a test that fails before doing so leaves
        // userGUID null, and there is nothing to clean up.
        if (userGUID != null) {
            accountManagementClient.deleteByUserGUID(userGUID);
        }
    }

    @Test
    void login_withMfaDisabledAndEmailVerifiedAndPasswordChangeNotRequired_completesAuthorizationCodeFlow() throws Exception {
        // 1. Create an account with default roles, MFA disabled, email verified, and no
        //    password change required — nothing should interrupt the login.
        email = "itest_" + UUID.randomUUID() + "@example.com";
        ResponseEntity<CreateTestAccountResponse> createResponse = accountManagementClient.createTestAccount(
                TEST_NAME, email, TEST_PASSWORD,
                false /* mfaEnabled */, true /* emailVerified */, false /* passwordChangeRequired */);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
        assertThat(createResponse.getBody()).isNotNull();
        userGUID = createResponse.getBody().userGUID();
        assertThat(userGUID).isNotBlank();

        // 2. Start the authorization-code flow so a SavedRequest is held in the session;
        //    a fully authenticated login redirects back to it (and thus to the callback).
        String redirectUri = webClientLocation + "/callback";
        HttpResponse<String> authorizeResponse =
                authServerClient.startOAuth2AuthorizationFlow("WEB_CLIENT", redirectUri, "openid WEB_CLIENT_READ", "test-state");
        assertThat(authorizeResponse.statusCode()).isEqualTo(302);
        HttpFlowUtils.followRedirects(authServerClient, authServerLocation, authorizeResponse, redirectUri);

        // 3. Log in. With MFA disabled, email verified, and no password change pending,
        //    the session is fully authenticated and redirected straight back to the
        //    saved authorize request — not to /ott/login, /reset-password, or
        //    /signup/success.
        HttpResponse<String> loginResponse = authServerClient.login(email, TEST_PASSWORD);
        assertThat(loginResponse.statusCode()).isEqualTo(302);
        assertThat(loginResponse.headers().firstValue("Location").orElseThrow()).contains("/oauth2/authorize");

        // 4. Follow the redirect chain; we should land on the web-client callback with
        //    an authorization code.
        HttpResponse<String> response = HttpFlowUtils.followRedirects(
                authServerClient, authServerLocation, loginResponse, redirectUri);
        assertThat(response.statusCode()).isEqualTo(302);
        String callback = response.headers().firstValue("Location").orElseThrow();
        assertThat(callback).startsWith(redirectUri);
        String code = HttpFlowUtils.extractQueryParam(callback, "code");
        assertThat(code).isNotBlank();

        // 5. Exchange the code for tokens and verify the right user is logged in with
        //    the default role.
        TokenResponse tokens = oAuth2Client.getAuthorizationGrantToken(code, "WEB_CLIENT", webClientSecret, redirectUri);
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.idToken()).isNotBlank();

        byte[] payloadBytes = Base64.getUrlDecoder().decode(tokens.accessToken().split("\\.")[1]);
        Map<String, Object> claims = new ObjectMapper().readValue(payloadBytes, new TypeReference<>() {});
        assertThat(claims.get("sub")).isEqualTo(email);
        List<String> roles = (List<String>) claims.get("roles");
        assertThat(roles).contains("MEMBER");
    }
}
