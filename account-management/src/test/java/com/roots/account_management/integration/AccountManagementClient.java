package com.roots.account_management.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Client for account-management's integration-test endpoints. Cookie-less (the API is
 * a stateless OAuth2 resource server); every call carries the client_credentials
 * access token obtained from the auth-server as a Bearer header.
 */
public class AccountManagementClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AccountManagementClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a test account via POST /api/account/test. Returns the raw response so the
     * test can assert on the status (201) and read the userGUID from the body.
     */
    public HttpResponse<String> createTestAccount(String accessToken, String name, String email, String password) throws Exception {
        String json = objectMapper.writeValueAsString(
                Map.of("name", name, "email", email, "password", password));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account/test"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Reads all fields of a test account by email via the protected
     * GET /api/account/test?email=... (requires the INTEGRATION_TEST_CLIENT_READ scope).
     * Returns the raw response (200 body is UserCredentialTestingResponse JSON, incl. password).
     */
    public HttpResponse<String> getTestAccountByEmail(String accessToken, String email) throws Exception {
        return getTestAccount(accessToken, "email=" + encode(email));
    }

    /**
     * Reads all fields of a test account by userGUID via the protected
     * GET /api/account/test?userGUID=... (requires the INTEGRATION_TEST_CLIENT_READ scope).
     * Returns the raw response (200 body is UserCredentialTestingResponse JSON, incl. password).
     */
    public HttpResponse<String> getTestAccountByUserGUID(String accessToken, String userGUID) throws Exception {
        return getTestAccount(accessToken, "userGUID=" + encode(userGUID));
    }

    /**
     * Deletes a test account by email via DELETE /api/account/test?email=...
     * Returns the raw response (204 on success).
     */
    public HttpResponse<String> deleteByEmail(String accessToken, String email) throws Exception {
        return delete(accessToken, "email=" + encode(email));
    }

    /**
     * Deletes a test account by userGUID via DELETE /api/account/test?userGUID=...
     * Returns the raw response (204 on success).
     */
    public HttpResponse<String> deleteByUserGUID(String accessToken, String userGUID) throws Exception {
        return delete(accessToken, "userGUID=" + encode(userGUID));
    }

    /**
     * Deletes with both email and userGUID present — an invalid combination the
     * validator rejects with 400.
     */
    public HttpResponse<String> deleteByEmailAndUserGUID(String accessToken, String email, String userGUID) throws Exception {
        return delete(accessToken, "email=" + encode(email) + "&userGUID=" + encode(userGUID));
    }

    /**
     * Deletes with neither email nor userGUID — an invalid combination the validator
     * rejects with 400.
     */
    public HttpResponse<String> deleteWithoutParams(String accessToken) throws Exception {
        return delete(accessToken, "");
    }

    /**
     * Extracts the userGUID field from a create-account 201 response body.
     */
    public String extractUserGUID(String createResponseBody) throws Exception {
        return objectMapper.readTree(createResponseBody).get("userGUID").asText();
    }

    private HttpResponse<String> getTestAccount(String accessToken, String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account/test?" + query))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String accessToken, String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account/test?" + query))
                .header("Authorization", "Bearer " + accessToken)
                .DELETE()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
