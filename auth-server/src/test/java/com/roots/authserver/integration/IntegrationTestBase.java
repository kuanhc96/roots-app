package com.roots.authserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.roots.authserver.client.AccountManagementClient;
import com.roots.authserver.client.AuthServerClient;
import com.roots.authserver.dto.TokenResponse;

/**
 * Base for the live-server integration tests. Builds a fresh {@link AuthServerClient} and
 * {@link OAuth2Client} before every test and closes them afterwards.
 *
 * <p>Per-test clients are deliberate: a shared client would pool a keep-alive connection
 * whose idle lifetime (JDK default 1200s) far outlives the live auth-server's Tomcat
 * keepAliveTimeout (20s). On a long suite the server closes the idle connection and the next
 * test reuses the now-dead one — a {@code ClosedChannelException} surfaced as
 * {@code ConnectException}. A fresh client per test removes that window (and gives each test a
 * clean cookie/session).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
abstract class IntegrationTestBase {

    @Value("${auth-server-location}")
    protected String authServerLocation;

    @Value("${account-management-location}")
    protected String accountManagementLocation;

    @Value("${integration-test-client-secret}")
    private String integrationTestClientSecret;

    protected AuthServerClient authServerClient;
    protected OAuth2Client oAuth2Client;
    protected AccountManagementClient accountManagementClient;

    @BeforeEach
    void buildClients() throws Exception {
        oAuth2Client = new OAuth2Client(authServerLocation);

        // Client-credentials token exchange for the integration-test client.
        TokenResponse ccToken = oAuth2Client.getClientCredentialsToken(
                "INTEGRATION_TEST_CLIENT", integrationTestClientSecret,
                "INTEGRATION_TEST_CLIENT_WRITE INTEGRATION_TEST_CLIENT_READ INTEGRATION_TEST_CLIENT_DELETE");
        assertThat(ccToken.accessToken()).isNotBlank();

        authServerClient = new AuthServerClient(authServerLocation, ccToken.accessToken());
        // RestTemplate-backed, no pooled connections to reap — nothing to close in @AfterEach.
        accountManagementClient = new AccountManagementClient(accountManagementLocation, ccToken.accessToken());
    }

    @AfterEach
    void closeClients() {
        authServerClient.close();
        oAuth2Client.close();
    }
}
