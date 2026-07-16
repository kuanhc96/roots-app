package com.roots.bff_server.client;

import com.roots.bff_server.dto.TokenResponse;

import tools.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HTTP client for a live auth-server, used by the bff integration tests to mint
 * genuine WEB_CLIENT tokens (slimmed-down port of auth-server's own integration-test
 * client). Owns and configures its {@link HttpClient}s: a cookie-bearing "browser"
 * for the redirect-driven login flow (redirects are never auto-followed, so every
 * hop stays observable) and a cookie-less machine client for the token-endpoint
 * exchange, which must not disturb the browser session.
 *
 * <p>{@code AutoCloseable}: built fresh per test and closed afterwards — a fresh
 * cookie jar per test means no session leaks between tests.
 */
public class AuthServerClient implements AutoCloseable {

    // A /login <-> /oauth2/authorize regression would redirect forever; cap the chain
    // so it fails the test instead of hanging the suite.
    private static final int MAX_REDIRECT_HOPS = 15;

    private final String baseUrl;
    private final String redirectUri;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient browser;
    private final HttpClient machineClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthServerClient(String baseUrl, String webClientLocation, String clientId, String clientSecret) {
        this.baseUrl = baseUrl;
        this.redirectUri = webClientLocation + "/callback";
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.browser = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.machineClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Drives a real guest login — authorize → {@code POST /login/guest} → redirect
     * chain to the callback → code exchange — and returns the token set. Guest needs
     * no account fixture, yet yields all three tokens (the openid scope produces an
     * id_token and WEB_CLIENT's grants include refresh_token).
     */
    public TokenResponse fetchGuestTokens() throws Exception {
        String authorizeUrl = baseUrl + "/oauth2/authorize?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode("openid WEB_CLIENT_READ")
                + "&state=bff-guest-test";
        HttpResponse<String> response = get(authorizeUrl);
        followRedirects(response);

        HttpRequest guestLogin = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login/guest"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        response = followRedirects(browser.send(guestLogin, HttpResponse.BodyHandlers.ofString()));

        if (response.statusCode() != 302) {
            throw new IllegalStateException("Guest login did not reach the callback; status "
                    + response.statusCode());
        }
        String callback = response.headers().firstValue("Location").orElseThrow();
        String code = extractQueryParam(callback, "code");

        return exchangeCode(code);
    }

    @Override
    public void close() {
        browser.close();
        machineClient.close();
    }

    private TokenResponse exchangeCode(String code) throws Exception {
        String basicAuth = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/oauth2/token"))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = machineClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Code exchange failed: " + response.statusCode()
                    + " " + response.body());
        }
        return objectMapper.readValue(response.body(), TokenResponse.class);
    }

    /** Walks the 302 chain on the browser session until the Location reaches the callback. */
    private HttpResponse<String> followRedirects(HttpResponse<String> response) throws Exception {
        int hops = 0;
        while (response.statusCode() == 302) {
            String location = response.headers().firstValue("Location").orElseThrow();
            if (location.startsWith(redirectUri)) {
                break;
            }
            if (++hops > MAX_REDIRECT_HOPS) {
                throw new IllegalStateException("Redirect chain exceeded " + MAX_REDIRECT_HOPS
                        + " hops; last Location: " + location);
            }
            response = get(location.startsWith("http") ? location : baseUrl + location);
        }
        return response;
    }

    private HttpResponse<String> get(String url) throws Exception {
        return browser.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String extractQueryParam(String url, String name) {
        for (String param : URI.create(url).getQuery().split("&")) {
            String[] kv = param.split("=", 2);
            if (kv[0].equals(name) && kv.length == 2) {
                return kv[1];
            }
        }
        throw new IllegalArgumentException("Parameter '" + name + "' not found in: " + url);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
