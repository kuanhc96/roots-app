package com.roots.authserver.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for account-management's endpoints, used from the auth-server integration
 * tests to drive cross-service flows against the shared DB. Backed by a
 * {@link RestTemplate} (no pooled connections to reap, so unlike {@link AuthServerClient}
 * it is not {@code AutoCloseable}).
 *
 * <p>The RestTemplate is configured with a lenient error handler so non-2xx responses
 * (400/404/409) come back as a {@link ResponseEntity} instead of throwing — the caller
 * asserts on {@code getStatusCode()} / {@code getBody()}, mirroring account-management's
 * own test client.
 *
 * <p>The GET and DELETE calls match account-management's client exactly; the POST is the
 * only difference — it lets the caller set the {@code mfaEnabled} / {@code emailVerified}
 * / {@code passwordChangeRequired} flags (and roles) explicitly.
 */
public class AccountManagementClient {

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HttpHeaders httpHeaders;
    private final String accessToken;

    public AccountManagementClient(String baseUrl, String accessToken) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.accessToken = accessToken;

        httpHeaders = bearerHeaders(accessToken);
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);

    }

    /**
     * Creates a test account via POST /api/account/test with the caller in full control of
     * the boolean flags and roles. Returns the raw response so the test can assert on the
     * status (201) and read the userGUID from the body.
     */
    public ResponseEntity<String> createTestAccount(String name, String email, String password,
                                                    boolean mfaEnabled, boolean emailVerified, boolean passwordChangeRequired,
                                                    List<String> roles) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("password", password);
        body.put("mfaEnabled", mfaEnabled);
        body.put("emailVerified", emailVerified);
        body.put("passwordChangeRequired", passwordChangeRequired);
        body.put("roles", roles);

        return restTemplate.exchange(
                baseUrl + "/api/account/test",
                HttpMethod.POST,
                new HttpEntity<>(body, httpHeaders),
                String.class
        );
    }

    /**
     * Reads all fields of a test account by email via the protected
     * GET /api/account/test?email=... (requires the INTEGRATION_TEST_CLIENT_READ scope).
     * Returns the raw response (200 body is UserCredentialTestingResponse JSON, incl. password).
     */
    public ResponseEntity<String> getTestAccountByEmail(String email) {
        return getTestAccount("email=" + email);
    }

    /**
     * Reads all fields of a test account by userGUID via the protected
     * GET /api/account/test?userGUID=... (requires the INTEGRATION_TEST_CLIENT_READ scope).
     * Returns the raw response (200 body is UserCredentialTestingResponse JSON, incl. password).
     */
    public ResponseEntity<String> getTestAccountByUserGUID(String userGUID) {
        return getTestAccount("userGUID=" + userGUID);
    }

    /**
     * Deletes a test account by email via DELETE /api/account/test?email=...
     * Returns the raw response (204 on success).
     */
    public ResponseEntity<String> deleteByEmail(String email) {
        return delete("email=" + email);
    }

    /**
     * Deletes a test account by userGUID via DELETE /api/account/test?userGUID=...
     * Returns the raw response (204 on success).
     */
    public ResponseEntity<String> deleteByUserGUID(String userGUID) {
        return delete("userGUID=" + userGUID);
    }

    /**
     * Deletes with both email and userGUID present — an invalid combination the
     * validator rejects with 400.
     */
    public ResponseEntity<String> deleteByEmailAndUserGUID(String email, String userGUID) {
        return delete("email=" + email + "&userGUID=" + userGUID);
    }

    /**
     * Deletes with neither email nor userGUID — an invalid combination the validator
     * rejects with 400.
     */
    public ResponseEntity<String> deleteWithoutParams() {
        return delete("");
    }

    /**
     * Extracts the userGUID field from a create-account 201 response body.
     */
    public String extractUserGUID(String createResponseBody) throws Exception {
        return objectMapper.readTree(createResponseBody).get("userGUID").asText();
    }

    private ResponseEntity<String> getTestAccount(String query) {
        return restTemplate.exchange(
                baseUrl + "/api/account/test?" + query,
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(accessToken)),
                String.class
        );
    }

    private ResponseEntity<String> delete(String query) {
        return restTemplate.exchange(
                baseUrl + "/api/account/test?" + query,
                HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(accessToken)),
                String.class
        );
    }

    private static HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }
}
