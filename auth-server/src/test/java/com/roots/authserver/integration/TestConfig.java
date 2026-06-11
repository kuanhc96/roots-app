package com.roots.authserver.integration;

import org.springframework.context.annotation.Configuration;

/**
 * Empty configuration that anchors the integration tests' Spring context so
 * {@code @TestPropertySource}/{@code @Value} can resolve the connection settings.
 * The HTTP clients are no longer beans — each test builds and closes its own
 * (see {@link IntegrationTestBase}) so no connection is shared across tests.
 */
@Configuration
public class TestConfig {
}
