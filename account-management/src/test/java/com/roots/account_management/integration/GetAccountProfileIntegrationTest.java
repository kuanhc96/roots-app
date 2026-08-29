package com.roots.account_management.integration;

import com.roots.account_management.dto.response.AccountProfileResponse;
import com.roots.account_management.dto.response.AccountProfilesResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({SpringExtension.class})
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class GetAccountProfileIntegrationTest {

    private static final String TEST_NAME_PREFIX = "Integration Test User ";
    private static final String TEST_PASSWORD = "Password123";

    @Autowired
    private AccountManagementClient accountManagementClient;

    private String userGUID;
    private String email;
    private String name;

    @BeforeEach
    void setUp() throws Exception {
        email = TestUtils.getUniqueEmail();
        name = TEST_NAME_PREFIX + UUID.randomUUID();

        ResponseEntity<CreateTestAccountResponse> createResponse =
                accountManagementClient.createTestAccount(name, email, TEST_PASSWORD);
        assertThat(createResponse.getStatusCode().value()).isEqualTo(201);
        userGUID = createResponse.getBody().userGUID();
        assertThat(userGUID).isNotBlank();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (userGUID != null) {
            ResponseEntity<Void> deleteResponse = accountManagementClient.deleteByUserGUID(userGUID);
            assertThat(deleteResponse.getStatusCode().value()).isIn(200, 204);
        }
    }

    @Test
    void getAccountProfile_byEmail_returnsUserGUIDEmailAndName() throws Exception {
        ResponseEntity<AccountProfileResponse> response = accountManagementClient.getAccountProfileByEmail(email);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().userGUID()).isEqualTo(userGUID);
        assertThat(response.getBody().email()).isEqualTo(email);
        assertThat(response.getBody().name()).isEqualTo(name);
    }

    @Test
    void getAccountProfile_byUserGUID_returnsUserGUIDEmailAndName() throws Exception {
        ResponseEntity<AccountProfileResponse> response = accountManagementClient.getAccountProfileByUserGUID(userGUID);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().userGUID()).isEqualTo(userGUID);
        assertThat(response.getBody().email()).isEqualTo(email);
        assertThat(response.getBody().name()).isEqualTo(name);
    }

    @Test
    void getAccountProfile_withBothEmailAndUserGUID_returns400() throws Exception {
        ResponseEntity<AccountProfileResponse> response = accountManagementClient.getAccountProfileWithQuery(
                "email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8)
                        + "&userGUID=" + java.net.URLEncoder.encode(userGUID, java.nio.charset.StandardCharsets.UTF_8));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void getAccountProfile_withNeitherEmailNorUserGUID_returns400() throws Exception {
        ResponseEntity<AccountProfileResponse> response = accountManagementClient.getAccountProfileWithQuery("");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void getAccountProfile_withUnknownUserGUID_returns404() throws Exception {
        ResponseEntity<AccountProfileResponse> response =
                accountManagementClient.getAccountProfileByUserGUID(UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void getAccountProfiles_withPageAndSize_returnsMetadataAndIncludesCreatedAccount() throws Exception {
        List<String> extraUserGUIDs = new ArrayList<>();
        try {
            ResponseEntity<CreateTestAccountResponse> extra1 =
                    accountManagementClient.createTestAccount(TEST_NAME_PREFIX + UUID.randomUUID(), TestUtils.getUniqueEmail(), TEST_PASSWORD);
            assertThat(extra1.getStatusCode().value()).isEqualTo(201);
            extraUserGUIDs.add(extra1.getBody().userGUID());

            ResponseEntity<CreateTestAccountResponse> extra2 =
                    accountManagementClient.createTestAccount(TEST_NAME_PREFIX + UUID.randomUUID(), TestUtils.getUniqueEmail(), TEST_PASSWORD);
            assertThat(extra2.getStatusCode().value()).isEqualTo(201);
            extraUserGUIDs.add(extra2.getBody().userGUID());

            ResponseEntity<AccountProfilesResponse> firstPage = accountManagementClient.getAccountProfiles(0, 20);
            assertThat(firstPage.getStatusCode().value()).isEqualTo(200);
            long totalElements = firstPage.getBody().totalElements();
            int lastPage = (int) ((totalElements - 1) / 20);

            ResponseEntity<AccountProfilesResponse> targetPage = accountManagementClient.getAccountProfiles(lastPage, 20);
            assertThat(targetPage.getStatusCode().value()).isEqualTo(200);

            assertThat(targetPage.getBody().page()).isEqualTo(lastPage);
            assertThat(targetPage.getBody().size()).isEqualTo(20);
            assertThat(targetPage.getBody().totalElements()).isGreaterThanOrEqualTo(3);
            assertThat(targetPage.getBody().accounts()).isNotNull();

            boolean found = false;
            for (AccountProfileResponse account : targetPage.getBody().accounts()) {
                if (userGUID.equals(account.userGUID())) {
                    found = true;
                    assertThat(account.email()).isEqualTo(email);
                    assertThat(account.name()).isEqualTo(name);
                    break;
                }
            }
            assertThat(found).isTrue();
        } finally {
            for (String guid : extraUserGUIDs) {
                accountManagementClient.deleteByUserGUID(guid);
            }
        }
    }

    @Test
    void getAccountProfiles_withPageBeyondRange_returns200WithEmptyAccounts() throws Exception {
        ResponseEntity<AccountProfilesResponse> response = accountManagementClient.getAccountProfiles(9999, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().page()).isEqualTo(9999);
        assertThat(response.getBody().size()).isEqualTo(20);
        assertThat(response.getBody().accounts()).isNotNull();
        assertThat(response.getBody().accounts().size()).isEqualTo(0);
    }

    @Test
    void getAccountProfiles_withoutParams_usesDefaults() throws Exception {
        ResponseEntity<AccountProfilesResponse> response = accountManagementClient.getAccountProfilesWithQuery("");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().page()).isEqualTo(0);
        assertThat(response.getBody().size()).isEqualTo(20);
        assertThat(response.getBody().accounts()).isNotNull();
    }

    @Test
    void getAccountProfiles_withNegativePage_returns400() throws Exception {
        ResponseEntity<AccountProfilesResponse> response = accountManagementClient.getAccountProfiles(-1, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void getAccountProfiles_withSizeZero_returns400() throws Exception {
        ResponseEntity<AccountProfilesResponse> response = accountManagementClient.getAccountProfiles(0, 0);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void getAccountProfiles_withSizeOverMax_returns400() throws Exception {
        ResponseEntity<AccountProfilesResponse> response = accountManagementClient.getAccountProfiles(0, 101);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void searchAccounts_byEmail_fullMatchTrue_returnsCreatedAccount() throws Exception {
        ResponseEntity<List<AccountProfileResponse>> response = accountManagementClient.searchAccountsByEmail(email, true, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        boolean found = false;
        for (AccountProfileResponse account : response.getBody()) {
            if (userGUID.equals(account.userGUID())) {
                found = true;
                assertThat(account.email()).isEqualTo(email);
                assertThat(account.name()).isEqualTo(name);
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void searchAccounts_byEmail_fullMatchFalse_supportsFuzzyContains() throws Exception {
        String localPart = email.substring(0, email.indexOf('@'));
        String fragment = localPart.substring(0, Math.min(6, localPart.length()));

        ResponseEntity<List<AccountProfileResponse>> response = accountManagementClient.searchAccountsByEmail(fragment, false, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        boolean found = false;
        for (AccountProfileResponse account : response.getBody()) {
            if (userGUID.equals(account.userGUID())) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void searchAccounts_byName_fullMatchTrue_returnsCreatedAccount() throws Exception {
        ResponseEntity<List<AccountProfileResponse>> response = accountManagementClient.searchAccountsByName(name, true, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        boolean found = false;
        for (AccountProfileResponse account : response.getBody()) {
            if (userGUID.equals(account.userGUID())) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void searchAccounts_byName_fullMatchFalse_supportsFuzzyContains() throws Exception {
        ResponseEntity<List<AccountProfileResponse>> response = accountManagementClient.searchAccountsByName("integration test", false, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        boolean found = false;
        for (AccountProfileResponse account : response.getBody()) {
            if (userGUID.equals(account.userGUID())) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void searchAccounts_withBothEmailAndName_returns400() throws Exception {
        ResponseEntity<List<AccountProfileResponse>> response = accountManagementClient.searchAccountsWithQuery(
                "email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8)
                        + "&name=" + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void searchAccounts_withNeitherEmailNorName_returns400() throws Exception {
        ResponseEntity<List<AccountProfileResponse>> response = accountManagementClient.searchAccountsWithQuery("");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void searchAccounts_withNonPositiveMaxCount_returns400() throws Exception {
        ResponseEntity<List<AccountProfileResponse>> response = accountManagementClient.searchAccountsWithQuery(
                "email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8) + "&maxCount=0");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void searchAccounts_respectsMaxCountLimit() throws Exception {
        ResponseEntity<List<AccountProfileResponse>> response = accountManagementClient.searchAccountsByName(name, true, 1);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().size()).isLessThanOrEqualTo(1);
    }
}
