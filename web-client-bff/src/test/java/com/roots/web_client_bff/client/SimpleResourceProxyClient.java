package com.roots.web_client_bff.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * HTTP client for simple-resource-server endpoints proxied through web-client-bff.
 */
public class SimpleResourceProxyClient implements AutoCloseable {

    private final String baseUrl;
    private final HttpClient httpClient;

    public SimpleResourceProxyClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public HttpResponse<String> getGuestRole(String sessionCookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/role/guest")).GET();
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
