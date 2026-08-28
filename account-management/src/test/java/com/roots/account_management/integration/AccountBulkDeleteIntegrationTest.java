package com.roots.account_management.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({SpringExtension.class})
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class AccountBulkDeleteIntegrationTest {

    private static final String TEST_NAME = "Integration Test User";
    private static final String TEST_PASSWORD = "Password123";
    private static final String SCOPES = "INTEGRATION_TEST_CLIENT_WRITE INTEGRATION_TEST_CLIENT_DELETE";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private OAuth2Client oAuth2Client;

    @Autowired
    private AccountManagementClient accountManagementClient;

    @Value("${integration-test-client-secret}")
    private String integrationTestClientSecret;

    private String accessToken;
    private String userGUID;
    private String email;

    @BeforeEach
    void setUp() throws Exception {
        accessToken = TestUtils.getClientCredentialsToken(oAuth2Client, integrationTestClientSecret, SCOPES);
        email = TestUtils.getUniqueEmail();

        HttpResponse<String> createResponse =
                accountManagementClient.createTestAccount(accessToken, TEST_NAME, email, TEST_PASSWORD);
        assertThat(createResponse.statusCode()).isEqualTo(201);
        userGUID = accountManagementClient.extractUserGUID(createResponse.body());
        assertThat(userGUID).isNotBlank();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (userGUID != null) {
            HttpResponse<String> deleteResponse = accountManagementClient.deleteByUserGUID(accessToken, userGUID);
            assertThat(deleteResponse.statusCode()).isIn(200, 204);
        }
    }

    @Test
    void deleteAccounts_withExistingMissingAndDuplicateUserGUIDs_returns204AndDeletesExisting() throws Exception {
        HttpResponse<String> deleteResponse = accountManagementClient.deleteAccounts(
                List.of(userGUID, " " + userGUID + " ", UUID.randomUUID().toString()));

        assertThat(deleteResponse.statusCode()).isEqualTo(204);

        HttpResponse<String> profileResponse = accountManagementClient.getAccountProfileByUserGUID(userGUID);
        assertThat(profileResponse.statusCode()).isEqualTo(404);
        userGUID = null;
    }

    @Test
    void deleteAccounts_withEmptyUserGUIDs_returns400() throws Exception {
        HttpResponse<String> response = accountManagementClient.deleteAccounts(List.of());

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void deleteAccounts_withMissingUserGUIDsField_returns400() throws Exception {
        HttpResponse<String> response = accountManagementClient.deleteAccountsRaw("{}");

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void deleteAccounts_withNullEntry_returns400() throws Exception {
        String body = OBJECT_MAPPER.writeValueAsString(new DeletePayload(List.of(userGUID, null)));
        HttpResponse<String> response = accountManagementClient.deleteAccountsRaw(body);

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void deleteAccounts_withBlankEntry_returns400() throws Exception {
        HttpResponse<String> response = accountManagementClient.deleteAccounts(List.of(userGUID, "   "));

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void deleteAccounts_withTooManyUserGUIDs_returns400() throws Exception {
        List<String> userGUIDs = List.of(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
        );
        HttpResponse<String> response = accountManagementClient.deleteAccounts(userGUIDs);

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    private record DeletePayload(List<String> userGUIDs) {
    }
}
