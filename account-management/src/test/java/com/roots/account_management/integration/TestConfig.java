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

    @Bean
    public OAuth2Client oAuth2Client() {
        return new OAuth2Client(authServerLocation);
    }

    @Bean
    public AccountManagementClient accountManagementClient() {
        return new AccountManagementClient(accountManagementLocation);
    }
}
