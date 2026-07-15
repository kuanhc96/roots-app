package com.roots.bff_server.integration;

import org.springframework.context.annotation.Configuration;

/**
 * Empty configuration that anchors the integration tests' Spring context so
 * {@code @TestPropertySource}/{@code @Value} can resolve the connection settings
 * (same pattern as auth-server's integration suite).
 */
@Configuration
public class TestConfig {
}
