package com.roots.bff_server.client;

import com.roots.bff_server.dto.response.TokenResponse;

import tools.jackson.databind.ObjectMapper;

import java.net.HttpCookie;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

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
    // Manual browser cookie jar: mirrors auth-server integration tests and allows
    // Secure cookies to be replayed on localhost HTTP test traffic.
    private final Map<String, HttpCookie> browserCookies = new LinkedHashMap<>();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public AuthServerClient(String baseUrl, String callbackLocation, String clientId, String clientSecret) {
        this.baseUrl = baseUrl;
        this.redirectUri = callbackLocation + "/api/auth/callback";
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.browser = HttpClient.newBuilder()
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
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = toS256CodeChallenge(codeVerifier);
        String authorizeUrl = baseUrl + "/oauth2/authorize?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode("openid WEB_CLIENT_READ")
                + "&code_challenge=" + encode(codeChallenge)
                + "&code_challenge_method=" + encode("S256")
                + "&state=bff-guest-test";
        String callback = completeGuestLogin(authorizeUrl, redirectUri);
        return exchangeCode(extractQueryParam(callback, "code"), codeVerifier);
    }

    /**
     * Plays the browser through a guest login starting from an externally built
     * authorize URL (e.g. the Location of bff-server's /api/auth/authorize redirect):
     * GET the authorize URL, {@code POST /login/guest}, follow the redirect chain
     * until the Location reaches {@code callbackPrefix} (the redirect_uri the
     * authorize URL carries), and return that final callback URL — whose query
     * carries {@code code} and {@code state} exactly as auth-server issued them.
     */
    public String completeGuestLogin(String authorizeUrl, String callbackPrefix) throws Exception {
        followRedirects(get(authorizeUrl), callbackPrefix);

        HttpRequest.Builder guestLogin = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login/guest"))
                .POST(HttpRequest.BodyPublishers.noBody());
        HttpResponse<String> response = followRedirects(
                sendWithCookies(guestLogin), callbackPrefix);

        if (response.statusCode() != 302) {
            throw new IllegalStateException("Guest login did not reach the callback; status "
                    + response.statusCode());
        }
        return response.headers().firstValue("Location").orElseThrow();
    }

    /**
     * Plays the browser through RP-Initiated Logout starting from an externally built
     * {@code /connect/logout} URL (e.g. the Location of bff-server's /api/auth/logout
     * redirect): GET it on the same cookie-bearing browser used for the login — so
     * auth-server's own session cookie rides along — and follow the redirect chain
     * until the Location reaches {@code postLogoutPrefix} (web-client's /logout).
     * Returns that final post-logout URL. With a valid {@code id_token_hint} and an
     * active session, Spring's OIDC logout skips its confirmation page and 302s
     * straight through.
     */
    public String completeLogout(String logoutUrl, String postLogoutPrefix) throws Exception {
        HttpResponse<String> response = followRedirects(get(logoutUrl), postLogoutPrefix);

        if (response.statusCode() != 302) {
            throw new IllegalStateException("Logout did not redirect to the post-logout URI; status "
                    + response.statusCode());
        }
        return response.headers().firstValue("Location").orElseThrow();
    }

    /** Extracts a query parameter from a callback URL (public for test assertions). */
    public static String queryParam(String url, String name) {
        return extractQueryParam(url, name);
    }

    @Override
    public void close() {
        browser.close();
        machineClient.close();
    }

    private TokenResponse exchangeCode(String code, String codeVerifier) throws Exception {
        String basicAuth = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri)
                + "&code_verifier=" + encode(codeVerifier);

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
    private HttpResponse<String> followRedirects(HttpResponse<String> response, String callbackPrefix) throws Exception {
        int hops = 0;
        while (response.statusCode() == 302) {
            String location = response.headers().firstValue("Location").orElseThrow();
            if (location.startsWith(callbackPrefix)) {
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
        return sendWithCookies(HttpRequest.newBuilder().uri(URI.create(url)).GET());
    }

    private HttpResponse<String> sendWithCookies(HttpRequest.Builder requestBuilder) throws Exception {
        buildCookieHeader(requestBuilder);
        String method = requestBuilder.build().method();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            String csrf = getCsrfTokenFromBrowserCookies();
            if (!csrf.isEmpty()) {
                requestBuilder.header("X-XSRF-TOKEN", csrf);
            }
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = browser.send(request, HttpResponse.BodyHandlers.ofString());
        captureSetCookies(response.headers());
        return response;
    }

    private void buildCookieHeader(HttpRequest.Builder requestBuilder) {
        if (!browserCookies.isEmpty()) {
            StringJoiner joiner = new StringJoiner("; ");
            for (HttpCookie cookie : browserCookies.values()) {
                joiner.add(cookie.getName() + "=" + cookie.getValue());
            }
            requestBuilder.header("Cookie", joiner.toString());
        }
    }

    private void captureSetCookies(HttpHeaders headers) {
        for (String setCookie : headers.allValues("set-cookie")) {
            List<HttpCookie> parsed = HttpCookie.parse(setCookie);
            if (parsed.isEmpty()) {
                continue;
            }
            HttpCookie cookie = parsed.get(0);
            if (cookie.getMaxAge() == 0) {
                browserCookies.remove(cookie.getName());
                continue;
            }
            browserCookies.put(cookie.getName(), cookie);
        }
    }

    private static String extractQueryParam(String url, String name) {
        for (String param : URI.create(url).getRawQuery().split("&")) {
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

    private String getCsrfTokenFromBrowserCookies() {
        HttpCookie csrfCookie = browserCookies.get("XSRF-TOKEN");
        return csrfCookie != null ? csrfCookie.getValue() : "";
    }

    private static String generateCodeVerifier() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String toS256CodeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
