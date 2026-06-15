package com.roots.account_management.integration;

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

/**
 * Integration test against a running account-management (and auth-server). Obtains a
 * client_credentials access token from the auth-server, then exercises the
 * integration-test-only create/delete endpoints on account-management.
 */
@ExtendWith({SpringExtension.class})
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class AccountLifecycleIntegrationTest {

    private static final String TEST_NAME = "Integration Test User";
    private static final String TEST_PASSWORD = "Password123";
    // Create requires WRITE, delete requires DELETE; one token carries both.
    private static final String SCOPES = "INTEGRATION_TEST_CLIENT_WRITE INTEGRATION_TEST_CLIENT_DELETE";

    @Autowired
    private OAuth2Client oAuth2Client;

    @Autowired
    private AccountManagementClient accountManagementClient;

    @Value("${integration-test-client-secret}")
    private String integrationTestClientSecret;

    @Test
    void createsThenDeletesTestAccountByEmail() throws Exception {
        String accessToken = clientCredentialsToken();
        String email = uniqueEmail();

        HttpResponse<String> createResponse =
                accountManagementClient.createTestAccount(accessToken, TEST_NAME, email, TEST_PASSWORD);
        assertThat(createResponse.statusCode()).isEqualTo(201);

        HttpResponse<String> deleteResponse = accountManagementClient.deleteByEmail(accessToken, email);
        assertThat(deleteResponse.statusCode()).isEqualTo(204);
    }

    @Test
    void createsThenDeletesTestAccountByUserGUID() throws Exception {
        String accessToken = clientCredentialsToken();
        String email = uniqueEmail();

        HttpResponse<String> createResponse =
                accountManagementClient.createTestAccount(accessToken, TEST_NAME, email, TEST_PASSWORD);
        assertThat(createResponse.statusCode()).isEqualTo(201);

        String userGUID = accountManagementClient.extractUserGUID(createResponse.body());
        assertThat(userGUID).isNotBlank();

        HttpResponse<String> deleteResponse = accountManagementClient.deleteByUserGUID(accessToken, userGUID);
        assertThat(deleteResponse.statusCode()).isEqualTo(204);
    }

    private String clientCredentialsToken() throws Exception {
        TokenResponse token = oAuth2Client.getClientCredentialsToken(
                "INTEGRATION_TEST_CLIENT", integrationTestClientSecret, SCOPES);
        assertThat(token.accessToken()).isNotBlank();
        return token.accessToken();
    }

    private static String uniqueEmail() {
        return "itest+" + UUID.randomUUID() + "@example.com";
    }
}
