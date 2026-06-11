package com.roots.authserver.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Helper dedicated to the auth-server's OAuth2 token endpoints. Stateless: it uses a
 * cookie-less HttpClient because token exchanges are out-of-band machine calls that
 * don't participate in the browser session held by {@link AuthServerClient}.
 */
public class OAuth2Client implements AutoCloseable {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OAuth2Client(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    /**
     * Performs a client_credentials token exchange (POST /oauth2/token) for a
     * machine-to-machine client such as INTEGRATION_TEST_CLIENT.
     */
    public TokenResponse getClientCredentialsToken(String clientId, String clientSecret, String scope) throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=client_credentials&scope=" + encode(scope);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/oauth2/token"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(response.body(), TokenResponse.class);
    }

    /**
     * Performs an authorization_code token exchange (POST /oauth2/token) for a
     * confidential client such as WEB_CLIENT, swapping the code for tokens.
     */
    public TokenResponse getAuthorizationGrantToken(String code, String clientId, String clientSecret, String redirectUri) throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/oauth2/token"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(response.body(), TokenResponse.class);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Closes the underlying HttpClient, releasing its pooled connections and selector
     * threads. Called per test so no idle connection is carried over to the next one.
     */
    @Override
    public void close() {
        httpClient.close();
    }
}
