package com.roots.authserver.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestConfig {

    @Value("${auth-server-location}")
    private String authServerLocation;

    @Bean
    public AuthServerClient authServerClient() {
        return new AuthServerClient(authServerLocation);
    }

    @Bean
    public OAuth2Client oAuth2Client() {
        return new OAuth2Client(authServerLocation);
    }
}
