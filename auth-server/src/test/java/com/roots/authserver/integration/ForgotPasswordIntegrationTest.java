package com.roots.authserver.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.authserver.dto.response.CreateAccountResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the forgot-password reset flow end-to-end against a live auth-server and
 * account-management. An account is created with passwordChangeRequired=true; a temp
 * password is minted, used to log in (landing on the reset step), and a new password is
 * set. The stored bcrypt hash is then verified to match the new password only.
 */
class ForgotPasswordIntegrationTest extends IntegrationTestBase {

    private static final String TEST_NAME = "Integration Test User";
    private static final String ORIGINAL_PASSWORD = "Password123";
    private static final String NEW_PASSWORD = "NewPassword456";

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private String email;
    private String userGUID;

    @BeforeEach
    void createTestAccount() throws Exception {
        email = "itest+" + UUID.randomUUID() + "@example.com";

        ResponseEntity<CreateTestAccountResponse> createResponse = accountManagementClient.createTestAccount(
                TEST_NAME, email, ORIGINAL_PASSWORD,
                false /* mfaEnabled */, true /* emailVerified */, true /* passwordChangeRequired */);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
        assertThat(createResponse.getBody()).isNotNull();

        userGUID = createResponse.getBody().userGUID();
        assertThat(userGUID).isNotBlank();
    }

    @AfterEach
    void deleteTestAccount() {
        accountManagementClient.deleteByUserGUID(userGUID);
    }

    @Test
    void forgotPassword_resetsToNewPassword() throws Exception {
        // 1. Mint a temp password for the account.
        HttpResponse<String> tempPasswordResponse = authServerClient.requestTempPassword(email);
        assertThat(tempPasswordResponse.statusCode()).isEqualTo(200);
        String tempPassword = tempPasswordResponse.body();
        assertThat(tempPassword).isNotBlank();

        // 2. Log in with the temp password. passwordChangeRequired=true, so we are routed
        //    to the reset step rather than fully authenticated.
        HttpResponse<String> loginResponse = authServerClient.login(email, tempPassword);
        assertThat(loginResponse.statusCode()).isEqualTo(302);
        assertThat(loginResponse.headers().firstValue("Location").orElseThrow()).endsWith("/reset-password");

        // 3. Set the new password.
        HttpResponse<String> resetResponse = authServerClient.resetPassword(NEW_PASSWORD);
        assertThat(resetResponse.statusCode()).isEqualTo(302);

        // 4. Read back the stored bcrypt hash and verify only the new password matches.
        ResponseEntity<UserCredentialTestingResponse> accountResponse = accountManagementClient.getTestAccountByUserGUID(userGUID);
        assertThat(accountResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(accountResponse.getBody()).isNotNull();
        String storedHash = accountResponse.getBody().password();

        assertThat(passwordEncoder.matches(NEW_PASSWORD, storedHash)).isTrue();
        assertThat(passwordEncoder.matches(tempPassword, storedHash)).isFalse();
        assertThat(passwordEncoder.matches(ORIGINAL_PASSWORD, storedHash)).isFalse();
    }
}
