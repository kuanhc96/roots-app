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

import org.springframework.http.ResponseEntity;
import java.util.ArrayList;
import java.util.List;
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
    private String duplicateEmailUserGUID;

    @BeforeEach
    void setUp() throws Exception {
        accessToken = TestUtils.getClientCredentialsToken(oAuth2Client, integrationTestClientSecret, SCOPES);
        email = TestUtils.getUniqueEmail();

        ResponseEntity<String> createResponse =
                accountManagementClient.createTestAccount(accessToken, TEST_NAME, email, TEST_PASSWORD);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
        userGUID = accountManagementClient.extractUserGUID(createResponse.getBody());
        assertThat(userGUID).isNotBlank();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (duplicateEmailUserGUID != null) {
            ResponseEntity<String> deleteResponse = accountManagementClient.deleteByUserGUID(accessToken, duplicateEmailUserGUID);
            assertThat(deleteResponse.getStatusCode().value()).isIn(200, 204);
        }
        if (userGUID != null) {
            ResponseEntity<String> deleteResponse = accountManagementClient.deleteByUserGUID(accessToken, userGUID);
            assertThat(deleteResponse.getStatusCode().value()).isIn(200, 204);
        }
    }

    @Test
    void updateMfaEnabled_changesFlagAndReturnsUpdatedStatus() throws Exception {
        ResponseEntity<String> response = accountManagementClient.updateMfaByUserGUID(userGUID, false);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = OBJECT_MAPPER.readTree(response.getBody());
        assertThat(body.get("userGUID").asText()).isEqualTo(userGUID);
        assertThat(body.get("mfaEnabled").asBoolean()).isFalse();
    }

    @Test
    void updateMfaEnabled_withMissingMfaEnabled_returns400() throws Exception {
        ResponseEntity<String> response = accountManagementClient.updateMfaByUserGUIDRaw(userGUID, "{}");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @Test
    void updateMfaEnabled_withUnknownUserGUID_returns404() throws Exception {
        ResponseEntity<String> response =
                accountManagementClient.updateMfaByUserGUID(UUID.randomUUID().toString(), false);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @Test
    void updatePassword_updatesStoredPasswordAndReturnsUserGUID() throws Exception {
        String newPassword = "NewPassword123";

        ResponseEntity<String> updateResponse = accountManagementClient.updatePasswordByUserGUID(userGUID, newPassword);
        assertThat(updateResponse.getStatusCode().value()).isEqualTo(200);
        JsonNode updateBody = OBJECT_MAPPER.readTree(updateResponse.getBody());
        assertThat(updateBody.get("userGUID").asText()).isEqualTo(userGUID);

        ResponseEntity<String> readResponse = accountManagementClient.getTestAccountByUserGUID(accessToken, userGUID);
        assertThat(readResponse.getStatusCode().value()).isEqualTo(200);
        JsonNode readBody = OBJECT_MAPPER.readTree(readResponse.getBody());
        String storedPassword = readBody.get("password").asText();
        assertThat(PASSWORD_ENCODER.matches(newPassword, storedPassword)).isTrue();
        assertThat(PASSWORD_ENCODER.matches(TEST_PASSWORD, storedPassword)).isFalse();
    }

    @Test
    void updatePassword_withMissingPassword_returns400() throws Exception {
        ResponseEntity<String> response = accountManagementClient.updatePasswordByUserGUIDRaw(userGUID, "{}");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @ParameterizedTest
    @MethodSource("invalidPasswords")
    void updatePassword_withInvalidPassword_returns400(String invalidPassword) throws Exception {
        ResponseEntity<String> response = accountManagementClient.updatePasswordByUserGUID(userGUID, invalidPassword);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @Test
    void updatePassword_withUnknownUserGUID_returns404() throws Exception {
        ResponseEntity<String> response =
                accountManagementClient.updatePasswordByUserGUID(UUID.randomUUID().toString(), "NewPassword123");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @Test
    void updateName_trimsWhitespacePersistsAndReturnsTrimmedName() throws Exception {
        String newName = "  Updated Integration Name  ";

        ResponseEntity<String> updateResponse = accountManagementClient.updateNameByUserGUID(userGUID, newName);
        assertThat(updateResponse.getStatusCode().value()).isEqualTo(200);
        JsonNode updateBody = OBJECT_MAPPER.readTree(updateResponse.getBody());
        assertThat(updateBody.get("userGUID").asText()).isEqualTo(userGUID);
        assertThat(updateBody.get("name").asText()).isEqualTo("Updated Integration Name");

        ResponseEntity<String> profileResponse = accountManagementClient.getAccountProfileByUserGUID(userGUID);
        assertThat(profileResponse.getStatusCode().value()).isEqualTo(200);
        JsonNode profileBody = OBJECT_MAPPER.readTree(profileResponse.getBody());
        assertThat(profileBody.get("name").asText()).isEqualTo("Updated Integration Name");
    }

    @Test
    void updateName_withMissingNameField_returns400() throws Exception {
        ResponseEntity<String> response = accountManagementClient.updateNameByUserGUIDRaw(userGUID, "{}");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void updateName_withInvalidName_returns400(String invalidName) throws Exception {
        ResponseEntity<String> response = accountManagementClient.updateNameByUserGUID(userGUID, invalidName);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @Test
    void updateName_withUnknownUserGUID_returns404() throws Exception {
        ResponseEntity<String> response =
                accountManagementClient.updateNameByUserGUID(UUID.randomUUID().toString(), "Updated Name");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @Test
    void updateEmail_trimsWhitespacePersistsAndReturnsTrimmedEmail() throws Exception {
        String newEmail = "  updated." + UUID.randomUUID() + "@example.com  ";
        String trimmedEmail = newEmail.trim();

        ResponseEntity<String> updateResponse = accountManagementClient.updateEmailByUserGUID(userGUID, newEmail);
        assertThat(updateResponse.getStatusCode().value()).isEqualTo(200);
        JsonNode updateBody = OBJECT_MAPPER.readTree(updateResponse.getBody());
        assertThat(updateBody.get("userGUID").asText()).isEqualTo(userGUID);
        assertThat(updateBody.get("email").asText()).isEqualTo(trimmedEmail);

        ResponseEntity<String> accountResponse = accountManagementClient.getAccountProfileByUserGUID(userGUID);
        assertThat(accountResponse.getStatusCode().value()).isEqualTo(200);
        JsonNode accountBody = OBJECT_MAPPER.readTree(accountResponse.getBody());
        assertThat(accountBody.get("email").asText()).isEqualTo(trimmedEmail);
    }

    @Test
    void updateEmail_withMissingEmailField_returns400() throws Exception {
        ResponseEntity<String> response = accountManagementClient.updateEmailByUserGUIDRaw(userGUID, "{}");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @ParameterizedTest
    @MethodSource("invalidEmails")
    void updateEmail_withInvalidEmail_returns400(String invalidEmail) throws Exception {
        ResponseEntity<String> response = accountManagementClient.updateEmailByUserGUID(userGUID, invalidEmail);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @Test
    void updateEmail_withDuplicateEmail_returns409() throws Exception {
        String duplicateEmail = TestUtils.getUniqueEmail();
        ResponseEntity<String> createResponse =
                accountManagementClient.createTestAccount(accessToken, TEST_NAME, duplicateEmail, TEST_PASSWORD);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
        duplicateEmailUserGUID = accountManagementClient.extractUserGUID(createResponse.getBody());
        assertThat(duplicateEmailUserGUID).isNotBlank();

        ResponseEntity<String> response = accountManagementClient.updateEmailByUserGUID(userGUID, duplicateEmail);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @Test
    void updateEmail_withUnknownUserGUID_returns404() throws Exception {
        ResponseEntity<String> response =
                accountManagementClient.updateEmailByUserGUID(UUID.randomUUID().toString(), TestUtils.getUniqueEmail());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @Test
    void addRole_withNewRole_returns201AndContainsAllRoles() throws Exception {
        ResponseEntity<String> response = accountManagementClient.addRoleByUserGUID(userGUID, "pastor");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode body = OBJECT_MAPPER.readTree(response.getBody());
        assertThat(body.get("userGUID").asText()).isEqualTo(userGUID);
        JsonNode roles = body.get("roles");
        assertThat(roles.isArray()).isTrue();
        List<String> roleList = new ArrayList<>();
        roles.forEach(r -> roleList.add(r.asText()));
        assertThat(roleList).contains("member", "pastor");
    }

    @Test
    void addRole_withAlreadyExistingRole_returns200() throws Exception {
        ResponseEntity<String> response = accountManagementClient.addRoleByUserGUID(userGUID, "member");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = OBJECT_MAPPER.readTree(response.getBody());
        assertThat(body.get("userGUID").asText()).isEqualTo(userGUID);
        JsonNode roles = body.get("roles");
        assertThat(roles.isArray()).isTrue();
        List<String> roleList = new ArrayList<>();
        roles.forEach(r -> roleList.add(r.asText()));
        assertThat(roleList).containsExactly("member");
    }

    @Test
    void addRole_withGuestRole_returns400() throws Exception {
        ResponseEntity<String> response = accountManagementClient.addRoleByUserGUID(userGUID, "guest");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @Test
    void addRole_withInvalidRole_returns400() throws Exception {
        ResponseEntity<String> response = accountManagementClient.addRoleByUserGUID(userGUID, "not_a_real_role");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @Test
    void addRole_withMissingRole_returns400() throws Exception {
        ResponseEntity<String> response = accountManagementClient.addRoleByUserGUIDRaw(userGUID, "{}");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.getBody());
    }

    @Test
    void addRole_withUnknownUserGUID_returns404() throws Exception {
        ResponseEntity<String> response = accountManagementClient.addRoleByUserGUID(UUID.randomUUID().toString(), "pastor");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        TestUtils.assertHasErrorField(response.getBody());
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

    private static Stream<Arguments> invalidEmails() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("not-an-email")
        );
    }
}
