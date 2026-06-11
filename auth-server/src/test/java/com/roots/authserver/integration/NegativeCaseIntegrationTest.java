package com.roots.authserver.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Negative-path integration tests against a live auth-server (localhost:9000).
 * Two concerns, split into @Nested groups:
 *   - {@link CreateAccountValidation}: POST /api/accounts rejects bad input (400) and
 *     duplicate emails (409).
 *   - {@link TestEndpointAuthorization}: the bearer-guarded POST /magic-link/generate/test
 *     rejects missing / wrong-scope / malformed tokens (401 / 403).
 */
class NegativeCaseIntegrationTest extends IntegrationTestBase {

    private static final String VALID_NAME = "Integration Test User";
    private static final String VALID_PASSWORD = "Password123";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${integration-test-client-secret}")
    private String integrationTestClientSecret;

    private static String uniqueEmail() {
        return "itest+" + UUID.randomUUID() + "@example.com";
    }

    private static void assertHasErrorField(String body) throws Exception {
        JsonNode json = OBJECT_MAPPER.readTree(body);
        assertThat(json.hasNonNull("error")).isTrue();
        assertThat(json.get("error").asText()).isNotBlank();
    }

    @Nested
    class CreateAccountValidation {

        // Each row exercises a distinct CreateAccountValidator branch. "Required"
        // branches are driven with empty strings (the validator checks isBlank()).
        static Stream<Arguments> invalidCreateAccountRequests() {
            String validEmail = "valid@example.com";
            return Stream.of(
                    // password rules
                    Arguments.of("password too short", VALID_NAME, validEmail, "Pass1"),
                    Arguments.of("password no uppercase", VALID_NAME, validEmail, "password123"),
                    Arguments.of("password no lowercase", VALID_NAME, validEmail, "PASSWORD123"),
                    Arguments.of("password no digit", VALID_NAME, validEmail, "PasswordOnly"),
                    Arguments.of("password blank", VALID_NAME, validEmail, ""),
                    // email rules
                    Arguments.of("email blank", VALID_NAME, "", VALID_PASSWORD),
                    Arguments.of("email missing @", VALID_NAME, "not-an-email", VALID_PASSWORD),
                    // name rules
                    Arguments.of("name blank", "", validEmail, VALID_PASSWORD),
                    Arguments.of("name too long", "a".repeat(256), validEmail, VALID_PASSWORD)
            );
        }

        @ParameterizedTest(name = "{0} -> 400")
        @MethodSource("invalidCreateAccountRequests")
        void createAccount_withInvalidInput_returns400(
                String caseName, String name, String email, String password) throws Exception {
            HttpResponse<String> response = authServerClient.createAccount(name, email, password);

            assertThat(response.statusCode()).isEqualTo(400);
            assertHasErrorField(response.body());
        }

        @Test
        void createAccount_withDuplicateEmail_returns409() throws Exception {
            String email = uniqueEmail();

            HttpResponse<String> first = authServerClient.createAccount(VALID_NAME, email, VALID_PASSWORD);
            assertThat(first.statusCode()).isEqualTo(201);

            HttpResponse<String> duplicate = authServerClient.createAccount(VALID_NAME, email, VALID_PASSWORD);
            assertThat(duplicate.statusCode()).isEqualTo(409);
            assertHasErrorField(duplicate.body());
        }
    }

    @Nested
    class TestEndpointAuthorization {

        @Test
        void generateMagicLinkToken_withoutBearerToken_returns401() throws Exception {
            HttpResponse<String> response = authServerClient.generateMagicLinkTokenWithoutAuth(uniqueEmail());

            assertThat(response.statusCode()).isEqualTo(401);
        }

        @Test
        void generateMagicLinkToken_withInsufficientScope_returns403() throws Exception {
            // A valid client_credentials token, but with READ instead of the required WRITE scope.
            TokenResponse readToken = oAuth2Client.getClientCredentialsToken(
                    "INTEGRATION_TEST_CLIENT", integrationTestClientSecret, "INTEGRATION_TEST_CLIENT_READ");
            assertThat(readToken.accessToken()).isNotBlank();

            HttpResponse<String> response =
                    authServerClient.generateMagicLinkTokenWithRawToken(readToken.accessToken(), uniqueEmail());

            assertThat(response.statusCode()).isEqualTo(403);
        }

        @Test
        void generateMagicLinkToken_withMalformedToken_returns401() throws Exception {
            HttpResponse<String> response =
                    authServerClient.generateMagicLinkTokenWithRawToken("not-a-real-jwt", uniqueEmail());

            assertThat(response.statusCode()).isEqualTo(401);
        }
    }
}
