package com.roots.authserver.integration;

import com.roots.authserver.dto.TokenResponse;

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
 *   - {@link CreateAccountValidation}: the POST /signup form post redirects bad input back
 *     to /signup with e=invalid_request and duplicate emails with e=email_taken.
 *   - {@link TestEndpointAuthorization}: the bearer-guarded POST /magic-link/generate/test
 *     rejects missing / wrong-scope / malformed tokens (401 / 403).
 */
class NegativeCaseIntegrationTest extends IntegrationTestBase {

    private static final String VALID_NAME = "Integration Test User";
    private static final String VALID_PASSWORD = "Password123";

    @Value("${integration-test-client-secret}")
    private String integrationTestClientSecret;

    private static String uniqueEmail() {
        return "itest+" + UUID.randomUUID() + "@example.com";
    }

    @Nested
    class CreateAccountValidation {

        // Each row exercises a distinct Validator.validateCreateAccountRequest branch.
        // "Required" branches are driven with empty strings (the validator checks isBlank()).
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

        @ParameterizedTest(name = "{0} -> e=invalid_request")
        @MethodSource("invalidCreateAccountRequests")
        void createAccount_withInvalidInput_redirectsWithInvalidRequest(
                String caseName, String name, String email, String password) throws Exception {
            HttpResponse<String> response = authServerClient.createAccount(name, email, password);

            assertThat(response.statusCode()).isEqualTo(302);
            String location = response.headers().firstValue("Location").orElseThrow();
            assertThat(location).contains("/signup?").contains("e=invalid_request");
        }

        @Test
        void createAccount_withDuplicateEmail_redirectsWithEmailTaken() throws Exception {
            String email = uniqueEmail();

            HttpResponse<String> first = authServerClient.createAccount(VALID_NAME, email, VALID_PASSWORD);
            assertThat(first.statusCode()).isEqualTo(302);
            assertThat(first.headers().firstValue("Location").orElseThrow()).endsWith("/signup/success");

            HttpResponse<String> duplicate = authServerClient.createAccount(VALID_NAME, email, VALID_PASSWORD);
            assertThat(duplicate.statusCode()).isEqualTo(302);
            String location = duplicate.headers().firstValue("Location").orElseThrow();
            assertThat(location).contains("/signup?").contains("e=email_taken");
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
