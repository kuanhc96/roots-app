package com.roots.authserver.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class AuthServerClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    // Cookie-less client for machine-to-machine calls (client_credentials token
    // exchange and the bearer-authenticated test endpoints). Carries no session
    // cookie, so these out-of-band calls cannot disturb the browser session held by
    // httpClient.
    private final HttpClient machineClient;
    private final ObjectMapper objectMapper;

    public AuthServerClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.machineClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    /**
     * Starts the Authorization Code flow (GET /oauth2/authorize) on the browser session
     * so a SavedRequest is held for the rest of the flow. Returns the auth-server's
     * immediate response; the caller asserts the 302 and follows the Location to the
     * login page (via {@link #getOnSession(String)}).
     */
    public HttpResponse<String> startOAuth2AuthorizationFlow(String clientId, String redirectUri, String scope, String state) throws Exception {
        String authorizeUrl = baseUrl + "/oauth2/authorize?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode(scope)
                + "&state=" + encode(state);

        return getOnSession(authorizeUrl);
    }

    /**
     * Submits POST /login/guest and returns the auth-server's immediate response.
     * The caller follows the resulting redirect chain (via {@link #getOnSession(String)})
     * and asserts on the status / Location.
     */
    public HttpResponse<String> loginAsGuest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login/guest"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Creates a new account via POST /api/accounts. Returns the raw response so the
     * test can assert on the status (201 on success).
     */
    public HttpResponse<String> createAccount(String name, String email, String password) throws Exception {
        String json = objectMapper.writeValueAsString(
                Map.of("name", name, "email", email, "password", password));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/accounts"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Performs form login (POST /login) and returns the raw response. The caller
     * inspects the status / Location header (an unverified account yields a 302 to
     * the "check your email" page).
     */
    public HttpResponse<String> login(String email, String password) throws Exception {
        String body = "email=" + encode(email) + "&password=" + encode(password);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Calls the integration-test-only POST /magic-link/generate/test endpoint with a
     * client_credentials access token to mint a magic-link token for the given email.
     * Runs on the cookie-less client; the email is passed explicitly rather than
     * inferred from a session. Returns the raw response (200 body is the token value).
     */
    public HttpResponse<String> generateMagicLinkToken(String accessToken, String email) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/magic-link/generate/test?email=" + encode(email)))
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return machineClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Completes magic-link verification on the browser session. The token is first
     * handed to the server via GET so it is captured into the HTTP session (the SPA
     * can't read it after hydration), then POST /magic-link/login consumes it from the
     * session. Returns the auth-server's immediate response to that POST; the caller
     * follows the resulting redirect chain (via {@link #getOnSession(String)}) and
     * asserts on the status / Location.
     */
    public HttpResponse<String> verifyMagicLink(String magicLinkToken) throws Exception {
        getOnSession(baseUrl + "/magic-link/login?magicLinkToken=" + encode(magicLinkToken));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/magic-link/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Issues a GET on the browser session (cookie-bearing client). Exposed so tests
     * can follow the redirect chain produced by the login / magic-link flows.
     */
    public HttpResponse<String> getOnSession(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
