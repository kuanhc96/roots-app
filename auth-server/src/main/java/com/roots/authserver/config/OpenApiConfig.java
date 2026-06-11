package com.roots.authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    /** Name referenced by {@code @SecurityRequirement} on the integration-test-only endpoints. */
    public static final String INTEGRATION_TEST_BEARER = "integrationTestClientCredentials";

    @Bean
    public OpenAPI authServerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("auth-server API")
                        .version("v1")
                        .description("""
                                OAuth2 Authorization Server for roots-app. Documents the account-creation,
                                MFA one-time-token, magic-link, and guest-login endpoints, plus the
                                integration-test-only endpoints that require an INTEGRATION_TEST_CLIENT
                                client_credentials access token."""))
                .components(new Components().addSecuritySchemes(INTEGRATION_TEST_BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("""
                                        INTEGRATION_TEST_CLIENT access token obtained via the client_credentials
                                        grant at POST /oauth2/token. Must carry the INTEGRATION_TEST_CLIENT_WRITE
                                        scope.""")));
    }
}
