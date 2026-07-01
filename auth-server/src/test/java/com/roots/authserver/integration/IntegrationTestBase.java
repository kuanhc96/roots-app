package com.roots.authserver.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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

    protected AuthServerClient authServerClient;
    protected OAuth2Client oAuth2Client;
    protected AccountManagementClient accountManagementClient;

    @BeforeEach
    void buildClients() {
        authServerClient = new AuthServerClient(authServerLocation);
        oAuth2Client = new OAuth2Client(authServerLocation);
        // RestTemplate-backed, no pooled connections to reap — nothing to close in @AfterEach.
        accountManagementClient = new AccountManagementClient(accountManagementLocation);
    }

    @AfterEach
    void closeClients() {
        authServerClient.close();
        oAuth2Client.close();
    }
}
