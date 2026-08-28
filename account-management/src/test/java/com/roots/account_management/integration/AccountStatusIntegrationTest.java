package com.roots.account_management.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({SpringExtension.class})
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class AccountStatusIntegrationTest {

    private static final String TEST_NAME = "Integration Test User";
    private static final String TEST_PASSWORD = "Password123";
    private static final String SCOPES = "INTEGRATION_TEST_CLIENT_WRITE INTEGRATION_TEST_CLIENT_READ INTEGRATION_TEST_CLIENT_DELETE";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final PasswordEncoder PASSWORD_ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

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
    void updateMfaEnabled_changesFlagAndReturnsUpdatedStatus() throws Exception {
        HttpResponse<String> response = accountManagementClient.updateMfaByUserGUID(userGUID, false);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        assertThat(body.get("userGUID").asText()).isEqualTo(userGUID);
        assertThat(body.get("mfaEnabled").asBoolean()).isFalse();
    }

    @Test
    void updateMfaEnabled_withMissingMfaEnabled_returns400() throws Exception {
        HttpResponse<String> response = accountManagementClient.updateMfaByUserGUIDRaw(userGUID, "{}");

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void updateMfaEnabled_withUnknownUserGUID_returns404() throws Exception {
        HttpResponse<String> response =
                accountManagementClient.updateMfaByUserGUID(UUID.randomUUID().toString(), false);

        assertThat(response.statusCode()).isEqualTo(404);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void updatePassword_updatesStoredPasswordAndReturnsUserGUID() throws Exception {
        String newPassword = "NewPassword123";

        HttpResponse<String> updateResponse = accountManagementClient.updatePasswordByUserGUID(userGUID, newPassword);
        assertThat(updateResponse.statusCode()).isEqualTo(200);
        JsonNode updateBody = OBJECT_MAPPER.readTree(updateResponse.body());
        assertThat(updateBody.get("userGUID").asText()).isEqualTo(userGUID);

        HttpResponse<String> readResponse = accountManagementClient.getTestAccountByUserGUID(accessToken, userGUID);
        assertThat(readResponse.statusCode()).isEqualTo(200);
        JsonNode readBody = OBJECT_MAPPER.readTree(readResponse.body());
        String storedPassword = readBody.get("password").asText();
        assertThat(PASSWORD_ENCODER.matches(newPassword, storedPassword)).isTrue();
        assertThat(PASSWORD_ENCODER.matches(TEST_PASSWORD, storedPassword)).isFalse();
    }

    @Test
    void updatePassword_withMissingPassword_returns400() throws Exception {
        HttpResponse<String> response = accountManagementClient.updatePasswordByUserGUIDRaw(userGUID, "{}");

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @ParameterizedTest
    @MethodSource("invalidPasswords")
    void updatePassword_withInvalidPassword_returns400(String invalidPassword) throws Exception {
        HttpResponse<String> response = accountManagementClient.updatePasswordByUserGUID(userGUID, invalidPassword);

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void updatePassword_withUnknownUserGUID_returns404() throws Exception {
        HttpResponse<String> response =
                accountManagementClient.updatePasswordByUserGUID(UUID.randomUUID().toString(), "NewPassword123");

        assertThat(response.statusCode()).isEqualTo(404);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void updateName_trimsWhitespacePersistsAndReturnsTrimmedName() throws Exception {
        String newName = "  Updated Integration Name  ";

        HttpResponse<String> updateResponse = accountManagementClient.updateNameByUserGUID(userGUID, newName);
        assertThat(updateResponse.statusCode()).isEqualTo(200);
        JsonNode updateBody = OBJECT_MAPPER.readTree(updateResponse.body());
        assertThat(updateBody.get("userGUID").asText()).isEqualTo(userGUID);
        assertThat(updateBody.get("name").asText()).isEqualTo("Updated Integration Name");

        HttpResponse<String> profileResponse = accountManagementClient.getAccountProfileByUserGUID(userGUID);
        assertThat(profileResponse.statusCode()).isEqualTo(200);
        JsonNode profileBody = OBJECT_MAPPER.readTree(profileResponse.body());
        assertThat(profileBody.get("name").asText()).isEqualTo("Updated Integration Name");
    }

    @Test
    void updateName_withMissingNameField_returns400() throws Exception {
        HttpResponse<String> response = accountManagementClient.updateNameByUserGUIDRaw(userGUID, "{}");

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void updateName_withInvalidName_returns400(String invalidName) throws Exception {
        HttpResponse<String> response = accountManagementClient.updateNameByUserGUID(userGUID, invalidName);

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void updateName_withUnknownUserGUID_returns404() throws Exception {
        HttpResponse<String> response =
                accountManagementClient.updateNameByUserGUID(UUID.randomUUID().toString(), "Updated Name");

        assertThat(response.statusCode()).isEqualTo(404);
        TestUtils.assertHasErrorField(response.body());
    }

    private static Stream<Arguments> invalidPasswords() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("short1A"),
                Arguments.of("password123"),
                Arguments.of("PASSWORD123"),
                Arguments.of("PasswordOnly")
        );
    }

    private static Stream<Arguments> invalidNames() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("a".repeat(256))
        );
    }
}
