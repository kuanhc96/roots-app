package com.roots.authserver.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AuthServerClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AuthServerClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public void startOAuth2AuthorizationFlow(String clientId, String redirectUri, String scope, String state) throws Exception {
        String authorizeUrl = baseUrl + "/oauth2/authorize?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode(scope)
                + "&state=" + encode(state);

        HttpResponse<String> response = get(authorizeUrl);

        if (response.statusCode() == 302) {
            String loginUrl = resolveLocation(response);
            get(loginUrl);
        }
    }

    public String loginAsGuest(String redirectUri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login/guest"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        while (response.statusCode() == 302) {
            String location = response.headers().firstValue("Location").orElseThrow();
            if (location.startsWith(redirectUri)) {
                return extractQueryParam(location, "code");
            }
            response = get(resolveLocation(response));
        }

        throw new IllegalStateException("Did not receive redirect to callback URI; last status: " + response.statusCode());
    }

    public TokenResponse exchangeCodeForToken(String code, String clientId, String clientSecret, String redirectUri) throws Exception {
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

    private HttpResponse<String> get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String resolveLocation(HttpResponse<?> response) {
        String location = response.headers().firstValue("Location").orElseThrow();
        return location.startsWith("http") ? location : baseUrl + location;
    }

    private static String extractQueryParam(String url, String name) {
        String query = URI.create(url).getQuery();
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv[0].equals(name) && kv.length == 2) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Parameter '" + name + "' not found in: " + url);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            String refreshToken,
            String idToken,
            String scope,
            String expiresIn
    ) {}
}
