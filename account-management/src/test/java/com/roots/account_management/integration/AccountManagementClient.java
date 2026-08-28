package com.roots.account_management.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Client for account-management integration tests.
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

    public HttpResponse<String> createTestAccount(String accessToken, String name, String email, String password) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("name", name, "email", email, "password", password));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account/test"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> getTestAccountByEmail(String accessToken, String email) throws Exception {
        return getTestAccount(accessToken, "email=" + encode(email));
    }

    public HttpResponse<String> getTestAccountByUserGUID(String accessToken, String userGUID) throws Exception {
        return getTestAccount(accessToken, "userGUID=" + encode(userGUID));
    }

    public HttpResponse<String> deleteByEmail(String accessToken, String email) throws Exception {
        return deleteTestAccount(accessToken, "email=" + encode(email));
    }

    public HttpResponse<String> deleteByUserGUID(String accessToken, String userGUID) throws Exception {
        return deleteTestAccount(accessToken, "userGUID=" + encode(userGUID));
    }

    public HttpResponse<String> deleteByEmailAndUserGUID(String accessToken, String email, String userGUID) throws Exception {
        return deleteTestAccount(accessToken, "email=" + encode(email) + "&userGUID=" + encode(userGUID));
    }

    public HttpResponse<String> deleteWithoutParams(String accessToken) throws Exception {
        return deleteTestAccount(accessToken, "");
    }

    public HttpResponse<String> deleteAccounts(List<String> userGUIDs) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("userGUIDs", userGUIDs));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account"))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> deleteAccountsRaw(String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account"))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public String extractUserGUID(String createResponseBody) throws Exception {
        return objectMapper.readTree(createResponseBody).get("userGUID").asText();
    }

    public HttpResponse<String> updateMfaByUserGUID(String userGUID, Boolean mfaEnabled) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("mfaEnabled", mfaEnabled));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account/mfa/" + encode(userGUID)))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> updateMfaByUserGUIDRaw(String userGUID, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account/mfa/" + encode(userGUID)))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> updatePasswordByUserGUID(String userGUID, String password) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("password", password));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account/password/" + encode(userGUID)))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> updatePasswordByUserGUIDRaw(String userGUID, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account/password/" + encode(userGUID)))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> getAccountProfileByEmail(String email) throws Exception {
        return getAccountProfile("email=" + encode(email));
    }

    public HttpResponse<String> getAccountProfileByUserGUID(String userGUID) throws Exception {
        return getAccountProfile("userGUID=" + encode(userGUID));
    }

    public HttpResponse<String> getAccountProfileWithQuery(String query) throws Exception {
        return getAccountProfile(query);
    }

    public HttpResponse<String> getAccountProfiles(int page, int size) throws Exception {
        return getAccountProfilesWithQuery("page=" + page + "&size=" + size);
    }

    public HttpResponse<String> getAccountProfilesWithQuery(String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account/profiles?" + query))
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> searchAccountsByEmail(String email, boolean fullMatch, Integer maxCount) throws Exception {
        String query = "email=" + encode(email) + "&fullMatch=" + fullMatch;
        if (maxCount != null) {
            query = query + "&maxCount=" + maxCount;
        }
        return searchAccountsWithQuery(query);
    }

    public HttpResponse<String> searchAccountsByName(String name, boolean fullMatch, Integer maxCount) throws Exception {
        String query = "name=" + encode(name) + "&fullMatch=" + fullMatch;
        if (maxCount != null) {
            query = query + "&maxCount=" + maxCount;
        }
        return searchAccountsWithQuery(query);
    }

    public HttpResponse<String> searchAccountsWithQuery(String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account/search?" + query))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getAccountProfile(String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account/profile?" + query))
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTestAccount(String accessToken, String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/account/test?" + query))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> deleteTestAccount(String accessToken, String query) throws Exception {
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
