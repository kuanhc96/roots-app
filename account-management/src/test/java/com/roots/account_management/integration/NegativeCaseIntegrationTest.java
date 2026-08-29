package com.roots.account_management.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.springframework.http.ResponseEntity;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Negative-path integration tests against a running account-management (and auth-server).
 * Mirrors the {@link Validator} branches, split into @Nested groups:
 *   - {@link CreateAccountValidation}: POST /api/account/test rejects bad input (400) and
 *     duplicate emails (409).
 *   - {@link DeleteAccountValidation}: DELETE /api/account/test rejects supplying both or
 *     neither of email/userGUID (400).
 * Each call carries an INTEGRATION_TEST_CLIENT client_credentials token, so failures are
 * the service's validation/conflict handling rather than authorization.
 */
@ExtendWith({SpringExtension.class})
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class NegativeCaseIntegrationTest {

    private static final String VALID_NAME = "Integration Test User";
    private static final String VALID_PASSWORD = "Password123";
    // Create requires WRITE, delete requires DELETE; one token carries both.
    private static final String SCOPES = "INTEGRATION_TEST_CLIENT_WRITE INTEGRATION_TEST_CLIENT_DELETE";

    @Autowired
    private AccountManagementClient accountManagementClient;

    @Nested
    class CreateAccountValidation {

        // Each row exercises a distinct Validator branch. "Required" branches are driven
        // with empty strings (the validator checks isBlank()).
        static Stream<Arguments> invalidCreateAccountRequests() {
            String validEmail = "valid@example.com";
            return Stream.of(
                    // password rules
                    Arguments.of("password too short", VALID_NAME, validEmail, "Pass1"),
                    Arguments.of("password no uppercase", VALID_NAME, validEmail, "password123"),
                    Arguments.of("password no lowercase", VALID_NAME, validEmail, "PASSWORD123"),
                    Arguments.of("password no digit", VALID_NAME, validEmail, "PasswordOnly"),
                    Arguments.of("password blank", VALID_NAME, validEmail, ""),
                    // email rules
                    Arguments.of("email blank", VALID_NAME, "", VALID_PASSWORD),
                    Arguments.of("email missing @", VALID_NAME, "not-an-email", VALID_PASSWORD),
                    // name rules
                    Arguments.of("name blank", "", validEmail, VALID_PASSWORD),
                    Arguments.of("name too long", "a".repeat(256), validEmail, VALID_PASSWORD)
            );
        }

        @ParameterizedTest(name = "{0} -> 400")
        @MethodSource("invalidCreateAccountRequests")
        void createTestAccount_withInvalidInput_returns400(
                String caseName, String name, String email, String password) throws Exception {
            ResponseEntity<String> response =
                    accountManagementClient.createTestAccount(name, email, password);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            TestUtils.assertHasErrorField(response.getBody());
        }

        @Test
        void createTestAccount_withDuplicateEmail_returns409() throws Exception {
            String email = TestUtils.getUniqueEmail();

            try {
                ResponseEntity<String> first =
                        accountManagementClient.createTestAccount(VALID_NAME, email, VALID_PASSWORD);
                assertThat(first.getStatusCode().value()).isEqualTo(201);

                ResponseEntity<String> duplicate =
                        accountManagementClient.createTestAccount(VALID_NAME, email, VALID_PASSWORD);
                assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
                TestUtils.assertHasErrorField(duplicate.getBody());
            } finally {
                // Teardown: remove the account the first create persisted (idempotent).
                accountManagementClient.deleteByEmail(email);
            }
        }
    }

    @Nested
    class DeleteAccountValidation {

        @Test
        void deleteTestAccount_withBothEmailAndUserGUID_returns400() throws Exception {
            ResponseEntity<String> response = accountManagementClient.deleteByEmailAndUserGUID(
                    TestUtils.getUniqueEmail(), UUID.randomUUID().toString());

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            TestUtils.assertHasErrorField(response.getBody());
        }

        @Test
        void deleteTestAccount_withNeitherEmailNorUserGUID_returns400() throws Exception {
            ResponseEntity<String> response = accountManagementClient.deleteWithoutParams();

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            TestUtils.assertHasErrorField(response.getBody());
        }
    }
}
