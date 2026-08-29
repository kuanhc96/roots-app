package com.roots.account_management.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.account_management.dto.request.UpdateNameRequest;
import com.roots.account_management.dto.request.UpdatePasswordRequest;
import com.roots.account_management.dto.response.AccountProfileResponse;
import com.roots.account_management.dto.response.AddRoleResponse;
import com.roots.account_management.dto.response.CreateTestAccountResponse;
import com.roots.account_management.dto.response.UpdateEmailResponse;
import com.roots.account_management.dto.response.UpdateMfaResponse;
import com.roots.account_management.dto.response.UpdateNameResponse;
import com.roots.account_management.dto.response.UpdatePasswordResponse;
import com.roots.account_management.dto.response.UserCredentialTestingResponse;
import com.roots.account_management.enums.Role;

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
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith({SpringExtension.class})
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class AccountStatusIntegrationTest {

    private static final String TEST_NAME = "Integration Test User";
    private static final String TEST_PASSWORD = "Password123";
    private static final PasswordEncoder PASSWORD_ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Autowired
    private AccountManagementClient accountManagementClient;

    private String userGUID;
    private String email;
    private String duplicateEmailUserGUID;

    @BeforeEach
    void setUp() throws Exception {
        email = TestUtils.getUniqueEmail();

        ResponseEntity<CreateTestAccountResponse> createResponse =
                accountManagementClient.createTestAccount(TEST_NAME, email, TEST_PASSWORD);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
        userGUID = createResponse.getBody().userGUID();
        assertThat(userGUID).isNotBlank();
    }

    @AfterEach
    void tearDown() {
        if (duplicateEmailUserGUID != null) {
            ResponseEntity<Void> deleteResponse = accountManagementClient.deleteByUserGUID(duplicateEmailUserGUID);
            assertThat(deleteResponse.getStatusCode().value()).isIn(200, 204);
        }
        if (userGUID != null) {
            ResponseEntity<Void> deleteResponse = accountManagementClient.deleteByUserGUID(userGUID);
            assertThat(deleteResponse.getStatusCode().value()).isIn(200, 204);
        }
    }

    @Test
    void updateMfaEnabled_changesFlagAndReturnsUpdatedStatus() throws Exception {
        ResponseEntity<UpdateMfaResponse> response = accountManagementClient.updateMfaByUserGUID(userGUID, false);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().userGUID()).isEqualTo(userGUID);
        assertThat(response.getBody().mfaEnabled()).isFalse();
    }

    @Test
    void updateMfaEnabled_withMissingMfaEnabled_returns400() {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.updateMfaByUserGUID(userGUID, null));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void updateMfaEnabled_withUnknownUserGUID_returns404() throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.updateMfaByUserGUID(UUID.randomUUID().toString(), false));

        assertThat(exception.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void updatePassword_updatesStoredPasswordAndReturnsUserGUID() throws Exception {
        String newPassword = "NewPassword123";

        ResponseEntity<UpdatePasswordResponse> updateResponse = accountManagementClient.updatePasswordByUserGUID(userGUID, newPassword);
        assertThat(updateResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(updateResponse.getBody().userGUID()).isEqualTo(userGUID);

        ResponseEntity<UserCredentialTestingResponse> readResponse = accountManagementClient.getTestAccountByUserGUID(userGUID);
        assertThat(readResponse.getStatusCode().value()).isEqualTo(200);
        String storedPassword = readResponse.getBody().password();
        assertThat(PASSWORD_ENCODER.matches(newPassword, storedPassword)).isTrue();
        assertThat(PASSWORD_ENCODER.matches(TEST_PASSWORD, storedPassword)).isFalse();
    }

    @Test
    void updatePassword_withMissingPassword_returns400() throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.updatePasswordByUserGUID(userGUID, null));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
    }

    @ParameterizedTest
    @MethodSource("invalidPasswords")
    void updatePassword_withInvalidPassword_returns400(String invalidPassword) throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.updatePasswordByUserGUID(userGUID, invalidPassword));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void updatePassword_withUnknownUserGUID_returns404() throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.updatePasswordByUserGUID(UUID.randomUUID().toString(), "NewPassword123"));

        assertThat(exception.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void updateName_trimsWhitespacePersistsAndReturnsTrimmedName() throws Exception {
        String newName = "  Updated Integration Name  ";

        ResponseEntity<UpdateNameResponse> updateResponse = accountManagementClient.updateNameByUserGUID(userGUID, newName);
        assertThat(updateResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(updateResponse.getBody().userGUID()).isEqualTo(userGUID);
        assertThat(updateResponse.getBody().name()).isEqualTo("Updated Integration Name");

        ResponseEntity<AccountProfileResponse> profileResponse = accountManagementClient.getAccountProfileByUserGUID(userGUID);
        assertThat(profileResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(profileResponse.getBody().name()).isEqualTo("Updated Integration Name");
    }

    @Test
    void updateName_withMissingNameField_returns400()  {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.updateNameByUserGUID(userGUID, null));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void updateName_withInvalidName_returns400(String invalidName) throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.updateNameByUserGUID(userGUID, invalidName));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void updateName_withUnknownUserGUID_returns404() throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.updateNameByUserGUID(UUID.randomUUID().toString(), "Updated Name"));

        assertThat(exception.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void updateEmail_trimsWhitespacePersistsAndReturnsTrimmedEmail() throws Exception {
        String newEmail = "  updated." + UUID.randomUUID() + "@example.com  ";
        String trimmedEmail = newEmail.trim();

        ResponseEntity<UpdateEmailResponse> updateResponse = accountManagementClient.updateEmailByUserGUID(userGUID, newEmail);
        assertThat(updateResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(updateResponse.getBody().userGUID()).isEqualTo(userGUID);
        assertThat(updateResponse.getBody().email()).isEqualTo(trimmedEmail);

        ResponseEntity<AccountProfileResponse> accountResponse = accountManagementClient.getAccountProfileByUserGUID(userGUID);
        assertThat(accountResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(accountResponse.getBody().email()).isEqualTo(trimmedEmail);
    }

    @Test
    void updateEmail_withMissingEmailField_returns400() throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.updateEmailByUserGUID(userGUID, null));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
    }

    @ParameterizedTest
    @MethodSource("invalidEmails")
    void updateEmail_withInvalidEmail_returns400(String invalidEmail) throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.updateEmailByUserGUID(userGUID, invalidEmail));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void updateEmail_withDuplicateEmail_returns409() throws Exception {
        String duplicateEmail = TestUtils.getUniqueEmail();
        ResponseEntity<CreateTestAccountResponse> createResponse =
                accountManagementClient.createTestAccount(TEST_NAME, duplicateEmail, TEST_PASSWORD);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
        duplicateEmailUserGUID = createResponse.getBody().userGUID();
        assertThat(duplicateEmailUserGUID).isNotBlank();

        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.updateEmailByUserGUID(userGUID, duplicateEmail));

        assertThat(exception.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void updateEmail_withUnknownUserGUID_returns404() throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.updateEmailByUserGUID(UUID.randomUUID().toString(), TestUtils.getUniqueEmail()));

        assertThat(exception.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void addRole_withNewRole_returns201AndContainsAllRoles() throws Exception {
        ResponseEntity<AddRoleResponse> response = accountManagementClient.addRoleByUserGUID(userGUID, Role.PASTOR);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().userGUID()).isEqualTo(userGUID);
        List<Role> roles = response.getBody().roles();
        assertThat(roles).isNotNull();
        assertThat(roles).contains(Role.MEMBER, Role.PASTOR);
    }

    @Test
    void addRole_withAlreadyExistingRole_returns200() throws Exception {
        ResponseEntity<AddRoleResponse> response = accountManagementClient.addRoleByUserGUID(userGUID, Role.MEMBER);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().userGUID()).isEqualTo(userGUID);
        List<Role> roles = response.getBody().roles();
        assertThat(roles).isNotNull();
        assertThat(roles).containsExactly(Role.MEMBER);
    }

    @Test
    void addRole_withGuestRole_returns400() throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.addRoleByUserGUID(userGUID, Role.GUEST));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void addRole_withMissingRole_returns400() throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.addRoleByUserGUID(userGUID, null));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void addRole_withUnknownUserGUID_returns404() throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.addRoleByUserGUID(UUID.randomUUID().toString(), Role.PASTOR));

        assertThat(exception.getStatusCode().value()).isEqualTo(404);
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
