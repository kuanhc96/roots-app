package com.roots.bff_server.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * HTTP client for a live bff-server, used by the integration tests (mirrors the
 * per-server client convention of auth-server's test suite). Owns and configures the
 * underlying {@link HttpClient}: deliberately cookie-less — tests assert the SESSION
 * cookie on responses and replay it explicitly — and never follows redirects, so
 * every raw response stays observable. One method per bff endpoint; more will be
 * added as the bff grows.
 *
 * <p>{@code AutoCloseable}: built fresh per test and closed afterwards (per-test
 * lifecycle, same rationale as the other integration suites).
 */
public class BffClient implements AutoCloseable {

    private final String baseUrl;
    private final HttpClient httpClient;

    public BffClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Calls {@code GET /api/auth/status}, presenting the given session cookie
     * ({@code "SESSION=<value>"}) — or none when {@code null}, which makes the server
     * establish a fresh session and return its cookie on the response.
     */
    public HttpResponse<String> getLoginStatus(String sessionCookie) throws Exception {
        return get("/api/auth/status", sessionCookie);
    }

    /**
     * Calls {@code GET /api/auth/authorize} — the authorization-code kick-off. The
     * response is the raw 302 (redirects are never followed), so callers can assert
     * the Location and, with a {@code null} cookie, read the fresh SESSION cookie.
     */
    public HttpResponse<String> getAuthorize(String sessionCookie) throws Exception {
        return get("/api/auth/authorize", sessionCookie);
    }

    private HttpResponse<String> get(String path, String sessionCookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
        if (sessionCookie != null) {
            request.header("Cookie", sessionCookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void close() {
        httpClient.close();
    }
}
