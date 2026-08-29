package com.roots.account_management.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

import com.roots.account_management.dto.response.CreateTestAccountResponse;

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

    @Autowired
    private AccountManagementClient accountManagementClient;

    @Test
    void createsThenDeletesTestAccountByEmail() {
        String email = TestUtils.getUniqueEmail();

        ResponseEntity<CreateTestAccountResponse> createResponse =
                accountManagementClient.createTestAccount(TEST_NAME, email, TEST_PASSWORD);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);

        ResponseEntity<Void> deleteResponse = accountManagementClient.deleteByEmail(email);
        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void createsThenDeletesTestAccountByUserGUID() throws Exception {
        String email = TestUtils.getUniqueEmail();

        ResponseEntity<CreateTestAccountResponse> createResponse =
                accountManagementClient.createTestAccount(TEST_NAME, email, TEST_PASSWORD);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);

        String userGUID = createResponse.getBody().userGUID();
        assertThat(userGUID).isNotBlank();

        ResponseEntity<Void> deleteResponse = accountManagementClient.deleteByUserGUID(userGUID);
        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(204);
    }
}
