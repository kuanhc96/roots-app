package com.roots.account_management.integration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for account-management integration tests.
 */
public class AccountManagementClient {

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HttpHeaders headers;

    public AccountManagementClient(String baseUrl, String accessToken) {
        this.baseUrl = baseUrl;
        headers = bearerJsonHeaders(accessToken);
        this.restTemplate = new RestTemplate();
        this.restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        this.objectMapper = new ObjectMapper();
    }

    public ResponseEntity<String> createTestAccount(String name, String email, String password) {
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("name", name, "email", email, "password", password), headers);
        return restTemplate.exchange(baseUrl + "/api/account/test", HttpMethod.POST, entity, String.class);
    }

    public ResponseEntity<String> getTestAccountByEmail(String email) {
        return getTestAccount("email=" + encode(email));
    }

    public ResponseEntity<String> getTestAccountByUserGUID(String userGUID) {
        return getTestAccount("userGUID=" + encode(userGUID));
    }

    public ResponseEntity<String> deleteByEmail(String email) {
        return deleteTestAccount("email=" + encode(email));
    }

    public ResponseEntity<String> deleteByUserGUID(String userGUID) {
        return deleteTestAccount("userGUID=" + encode(userGUID));
    }

    public ResponseEntity<String> deleteByEmailAndUserGUID(String email, String userGUID) {
        return deleteTestAccount("email=" + encode(email) + "&userGUID=" + encode(userGUID));
    }

    public ResponseEntity<String> deleteWithoutParams() {
        return deleteTestAccount("");
    }

    public ResponseEntity<String> deleteAccounts(List<String> userGUIDs) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<Map<String, List<String>>> entity = new HttpEntity<>(Map.of("userGUIDs", userGUIDs), headers);
        return restTemplate.exchange(baseUrl + "/api/account", HttpMethod.DELETE, entity, String.class);
    }

    public ResponseEntity<String> deleteAccountsRaw(String jsonBody) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return restTemplate.exchange(baseUrl + "/api/account", HttpMethod.DELETE, entity, String.class);
    }

    public String extractUserGUID(String body) throws Exception {
        return objectMapper.readTree(body).get("userGUID").asText();
    }

    public ResponseEntity<String> updateMfaByUserGUID(String userGUID, Boolean mfaEnabled) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<Map<String, Boolean>> entity = new HttpEntity<>(Map.of("mfaEnabled", mfaEnabled), headers);
        return restTemplate.exchange(baseUrl + "/api/account/mfa/" + encode(userGUID), HttpMethod.PUT, entity, String.class);
    }

    public ResponseEntity<String> updateMfaByUserGUIDRaw(String userGUID, String jsonBody) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return restTemplate.exchange(baseUrl + "/api/account/mfa/" + encode(userGUID), HttpMethod.PUT, entity, String.class);
    }

    public ResponseEntity<String> updatePasswordByUserGUID(String userGUID, String password) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("password", password), headers);
        return restTemplate.exchange(baseUrl + "/api/account/password/" + encode(userGUID), HttpMethod.PUT, entity, String.class);
    }

    public ResponseEntity<String> updatePasswordByUserGUIDRaw(String userGUID, String jsonBody) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return restTemplate.exchange(baseUrl + "/api/account/password/" + encode(userGUID), HttpMethod.PUT, entity, String.class);
    }

    public ResponseEntity<String> updateNameByUserGUID(String userGUID, String name) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("name", name), headers);
        return restTemplate.exchange(baseUrl + "/api/account/name/" + encode(userGUID), HttpMethod.PUT, entity, String.class);
    }

    public ResponseEntity<String> updateNameByUserGUIDRaw(String userGUID, String jsonBody) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return restTemplate.exchange(baseUrl + "/api/account/name/" + encode(userGUID), HttpMethod.PUT, entity, String.class);
    }

    public ResponseEntity<String> updateEmailByUserGUID(String userGUID, String email) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("email", email), headers);
        return restTemplate.exchange(baseUrl + "/api/account/email/" + encode(userGUID), HttpMethod.PUT, entity, String.class);
    }

    public ResponseEntity<String> updateEmailByUserGUIDRaw(String userGUID, String jsonBody) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return restTemplate.exchange(baseUrl + "/api/account/email/" + encode(userGUID), HttpMethod.PUT, entity, String.class);
    }

    public ResponseEntity<String> addRoleByUserGUID(String userGUID, String role) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("role", role), headers);
        return restTemplate.exchange(baseUrl + "/api/account/role/" + encode(userGUID), HttpMethod.POST, entity, String.class);
    }

    public ResponseEntity<String> addRoleByUserGUIDRaw(String userGUID, String jsonBody) {
        HttpHeaders headers = jsonHeaders();
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return restTemplate.exchange(baseUrl + "/api/account/role/" + encode(userGUID), HttpMethod.POST, entity, String.class);
    }

    public ResponseEntity<String> getAccountProfileByEmail(String email) {
        return getAccountProfile("email=" + encode(email));
    }

    public ResponseEntity<String> getAccountProfileByUserGUID(String userGUID) {
        return getAccountProfile("userGUID=" + encode(userGUID));
    }

    public ResponseEntity<String> getAccountProfileWithQuery(String query) {
        return getAccountProfile(query);
    }

    public ResponseEntity<String> getAccountProfiles(int page, int size) {
        return getAccountProfilesWithQuery("page=" + page + "&size=" + size);
    }

    public ResponseEntity<String> getAccountProfilesWithQuery(String query) {
        String url = baseUrl + "/api/account/profiles" + (query.isBlank() ? "" : "?" + query);
        return restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }

    public ResponseEntity<String> searchAccountsByEmail(String email, boolean fullMatch, Integer maxCount) {
        String query = "email=" + encode(email) + "&fullMatch=" + fullMatch;
        if (maxCount != null) {
            query = query + "&maxCount=" + maxCount;
        }
        return searchAccountsWithQuery(query);
    }

    public ResponseEntity<String> searchAccountsByName(String name, boolean fullMatch, Integer maxCount) {
        String query = "name=" + encode(name) + "&fullMatch=" + fullMatch;
        if (maxCount != null) {
            query = query + "&maxCount=" + maxCount;
        }
        return searchAccountsWithQuery(query);
    }

    public ResponseEntity<String> searchAccountsWithQuery(String query) {
        String url = baseUrl + "/api/account/search" + (query.isBlank() ? "" : "?" + query);
        return restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
    }

    private ResponseEntity<String> getAccountProfile(String query) {
        String url = baseUrl + "/api/account/profile" + (query.isBlank() ? "" : "?" + query);
        return restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }

    private ResponseEntity<String> getTestAccount(String query) {
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = baseUrl + "/api/account/test" + (query.isBlank() ? "" : "?" + query);
        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
    }

    private ResponseEntity<String> deleteTestAccount(String query) {
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = baseUrl + "/api/account/test" + (query.isBlank() ? "" : "?" + query);
        return restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders bearerJsonHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

