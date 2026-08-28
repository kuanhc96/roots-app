package com.roots.account_management.integration;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({SpringExtension.class})
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class GetAccountProfileIntegrationTest {

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
    void getAccountProfile_byEmail_returnsUserGUIDEmailAndName() throws Exception {
        HttpResponse<String> response = accountManagementClient.getAccountProfileByEmail(email);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        assertThat(body.get("userGUID").asText()).isEqualTo(userGUID);
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("name").asText()).isEqualTo(TEST_NAME);
    }

    @Test
    void getAccountProfile_byUserGUID_returnsUserGUIDEmailAndName() throws Exception {
        HttpResponse<String> response = accountManagementClient.getAccountProfileByUserGUID(userGUID);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        assertThat(body.get("userGUID").asText()).isEqualTo(userGUID);
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("name").asText()).isEqualTo(TEST_NAME);
    }

    @Test
    void getAccountProfile_withBothEmailAndUserGUID_returns400() throws Exception {
        HttpResponse<String> response = accountManagementClient.getAccountProfileWithQuery(
                "email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8)
                        + "&userGUID=" + java.net.URLEncoder.encode(userGUID, java.nio.charset.StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void getAccountProfile_withNeitherEmailNorUserGUID_returns400() throws Exception {
        HttpResponse<String> response = accountManagementClient.getAccountProfileWithQuery("");

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void getAccountProfile_withUnknownUserGUID_returns404() throws Exception {
        HttpResponse<String> response =
                accountManagementClient.getAccountProfileByUserGUID(UUID.randomUUID().toString());

        assertThat(response.statusCode()).isEqualTo(404);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void getAccountProfiles_withPageAndSize_returnsMetadataAndIncludesCreatedAccount() throws Exception {
        List<String> extraUserGUIDs = new ArrayList<>();
        try {
            HttpResponse<String> extra1 =
                    accountManagementClient.createTestAccount(accessToken, TEST_NAME, TestUtils.getUniqueEmail(), TEST_PASSWORD);
            assertThat(extra1.statusCode()).isEqualTo(201);
            extraUserGUIDs.add(accountManagementClient.extractUserGUID(extra1.body()));

            HttpResponse<String> extra2 =
                    accountManagementClient.createTestAccount(accessToken, TEST_NAME, TestUtils.getUniqueEmail(), TEST_PASSWORD);
            assertThat(extra2.statusCode()).isEqualTo(201);
            extraUserGUIDs.add(accountManagementClient.extractUserGUID(extra2.body()));

            HttpResponse<String> firstPage = accountManagementClient.getAccountProfiles(0, 20);
            assertThat(firstPage.statusCode()).isEqualTo(200);
            JsonNode firstBody = OBJECT_MAPPER.readTree(firstPage.body());
            long totalElements = firstBody.get("totalElements").asLong();
            int lastPage = (int) ((totalElements - 1) / 20);

            HttpResponse<String> targetPage = accountManagementClient.getAccountProfiles(lastPage, 20);
            assertThat(targetPage.statusCode()).isEqualTo(200);
            JsonNode body = OBJECT_MAPPER.readTree(targetPage.body());

            assertThat(body.get("page").asInt()).isEqualTo(lastPage);
            assertThat(body.get("size").asInt()).isEqualTo(20);
            assertThat(body.get("totalElements").asLong()).isGreaterThanOrEqualTo(3);
            assertThat(body.get("accounts").isArray()).isTrue();

            boolean found = false;
            for (JsonNode account : body.get("accounts")) {
                if (userGUID.equals(account.get("userGUID").asText())) {
                    found = true;
                    assertThat(account.get("email").asText()).isEqualTo(email);
                    assertThat(account.get("name").asText()).isEqualTo(TEST_NAME);
                    break;
                }
            }
            assertThat(found).isTrue();
        } finally {
            for (String guid : extraUserGUIDs) {
                accountManagementClient.deleteByUserGUID(accessToken, guid);
            }
        }
    }

    @Test
    void getAccountProfiles_withPageBeyondRange_returns200WithEmptyAccounts() throws Exception {
        HttpResponse<String> response = accountManagementClient.getAccountProfiles(9999, 20);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        assertThat(body.get("page").asInt()).isEqualTo(9999);
        assertThat(body.get("size").asInt()).isEqualTo(20);
        assertThat(body.get("accounts").isArray()).isTrue();
        assertThat(body.get("accounts").size()).isEqualTo(0);
    }

    @Test
    void getAccountProfiles_withoutParams_usesDefaults() throws Exception {
        HttpResponse<String> response = accountManagementClient.getAccountProfilesWithQuery("");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        assertThat(body.get("page").asInt()).isEqualTo(0);
        assertThat(body.get("size").asInt()).isEqualTo(20);
        assertThat(body.get("accounts").isArray()).isTrue();
    }

    @Test
    void getAccountProfiles_withNegativePage_returns400() throws Exception {
        HttpResponse<String> response = accountManagementClient.getAccountProfilesWithQuery("page=-1&size=20");

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void getAccountProfiles_withSizeZero_returns400() throws Exception {
        HttpResponse<String> response = accountManagementClient.getAccountProfilesWithQuery("page=0&size=0");

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }

    @Test
    void getAccountProfiles_withSizeOverMax_returns400() throws Exception {
        HttpResponse<String> response = accountManagementClient.getAccountProfilesWithQuery("page=0&size=101");

        assertThat(response.statusCode()).isEqualTo(400);
        TestUtils.assertHasErrorField(response.body());
    }
}
