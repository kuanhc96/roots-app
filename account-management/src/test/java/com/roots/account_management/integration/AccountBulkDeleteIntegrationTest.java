package com.roots.account_management.integration;

import com.roots.account_management.dto.response.AccountProfileResponse;
import com.roots.account_management.dto.response.CreateTestAccountResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith({SpringExtension.class})
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class AccountBulkDeleteIntegrationTest {

    private static final String TEST_NAME = "Integration Test User";
    private static final String TEST_PASSWORD = "Password123";

    @Autowired
    private AccountManagementClient accountManagementClient;

    private String userGUID;
    private String email;



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
        if (userGUID != null) {
            ResponseEntity<Void> deleteResponse = accountManagementClient.deleteByUserGUID(userGUID);
            assertThat(deleteResponse.getStatusCode().value()).isIn(200, 204);
        }
    }

    @Test
    void deleteAccounts_withExistingMissingAndDuplicateUserGUIDs_returns204AndDeletesExisting() throws Exception {
        ResponseEntity<Void> deleteResponse = accountManagementClient.deleteAccounts(
                List.of(userGUID, " " + userGUID + " ", UUID.randomUUID().toString()));

        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(204);

        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.getAccountProfileByUserGUID(userGUID));
        assertThat(exception.getStatusCode().value()).isEqualTo(404);
        userGUID = null;
    }

    @Test
    void deleteAccounts_withEmptyUserGUIDs_returns400() throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.deleteAccounts(List.of()));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void deleteAccounts_withBlankEntry_returns400() throws Exception {
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.deleteAccounts(List.of(userGUID, "   ")));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
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
        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () -> accountManagementClient.deleteAccounts(userGUIDs));

        assertThat(exception.getStatusCode().value()).isEqualTo(400);
    }
}
