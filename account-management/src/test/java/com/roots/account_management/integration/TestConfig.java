package com.roots.account_management.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestConfig {

    @Value("${auth-server-location}")
    private String authServerLocation;

    @Value("${account-management-location}")
    private String accountManagementLocation;

    @Value("${integration-test-client-secret}")
    private String integrationTestClientSecret;

    @Bean
    public OAuth2Client oAuth2Client() {
        return new OAuth2Client(authServerLocation);
    }

    @Bean
    public AccountManagementClient accountManagementClient(OAuth2Client oAuth2Client) throws Exception {
        String accessToken = TestUtils.getClientCredentialsToken(oAuth2Client, integrationTestClientSecret, "INTEGRATION_TEST_CLIENT_READ INTEGRATION_TEST_CLIENT_WRITE INTEGRATION_TEST_CLIENT_DELETE");
        return new AccountManagementClient(accountManagementLocation, accessToken);
    }
}
