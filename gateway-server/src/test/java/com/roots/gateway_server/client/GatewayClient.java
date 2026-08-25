package com.roots.gateway_server.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * HTTP client for a live gateway-server used by integration tests.
 * Redirects are never auto-followed so each hop remains assertable.
 */
public class GatewayClient implements AutoCloseable {

    private final String baseUrl;
    private final HttpClient httpClient;

    public GatewayClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public HttpResponse<String> getLoginStatus(String sessionCookie) throws Exception {
        return get("/roots-app/bff-server/auth/status", sessionCookie);
    }

    public HttpResponse<String> getAuthorize(String sessionCookie) throws Exception {
        return get("/roots-app/bff-server/auth/authorize", sessionCookie);
    }

    public HttpResponse<String> getGuestRole(String sessionCookie) throws Exception {
        return get("/roots-app/simple-resource-server/role/guest", sessionCookie);
    }

    private HttpResponse<String> get(String path, String sessionCookie) throws Exception {
        return getUrl(baseUrl + path, sessionCookie);
    }

    private HttpResponse<String> getUrl(String url, String sessionCookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
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
