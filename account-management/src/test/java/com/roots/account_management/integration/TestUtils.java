package com.roots.account_management.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared helpers for the account-management integration tests.
 */
public final class TestUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TestUtils() {
    }

    /**
     * Performs a client_credentials exchange for INTEGRATION_TEST_CLIENT and returns the
     * access token, asserting it is present.
     */
    public static String getClientCredentialsToken(OAuth2Client oAuth2Client, String clientSecret, String scopes) throws Exception {
        TokenResponse token = oAuth2Client.getClientCredentialsToken(
                "INTEGRATION_TEST_CLIENT", clientSecret, scopes);
        assertThat(token.accessToken()).isNotBlank();
        return token.accessToken();
    }

    /** A unique email so repeated runs never collide on the UNIQUE(email) constraint. */
    public static String getUniqueEmail() {
        return "itest+" + UUID.randomUUID() + "@example.com";
    }

    /** Asserts the body is the {@code {"error": "..."}} shape with a non-blank message. */
    public static void assertHasErrorField(String body) throws Exception {
        JsonNode json = OBJECT_MAPPER.readTree(body);
        assertThat(json.hasNonNull("error")).isTrue();
        assertThat(json.get("error").asText()).isNotBlank();
    }
}
