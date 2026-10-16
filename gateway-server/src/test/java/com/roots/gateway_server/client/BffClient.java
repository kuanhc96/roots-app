package com.roots.gateway_server.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * HTTP client for a live web-client-bff used by gateway integration tests.
 */
public class BffClient implements AutoCloseable {

    private final HttpClient httpClient;
    private String baseUrl;

    public BffClient(String baseUrl) {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.baseUrl = baseUrl;
    }

    public HttpResponse<String> getCallback(String callbackUrl, String sessionCookie) throws Exception {
        return getUrl(callbackUrl, sessionCookie);
    }

    public HttpResponse<String> getLoginStatus(String sessionCookie) throws Exception {
        return getUrl(baseUrl + "/api/auth/status", sessionCookie);
    }

    public HttpResponse<String> getAuthorize(String sessionCookie) throws Exception {
        return getUrl(baseUrl + "/api/auth/authorize", sessionCookie);
    }

    private HttpResponse<String> getUrl(String url, String sessionCookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
        if (sessionCookie != null) {
            request.header("Cookie", sessionCookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> getGuestRole(String sessionCookie) throws Exception {
        return get(baseUrl + "/api/role/guest", sessionCookie);
    }

    private HttpResponse<String> get(String path, String sessionCookie) throws Exception {
        return getUrl(baseUrl + path, sessionCookie);
    }

    @Override
    public void close() {
        httpClient.close();
    }
}
