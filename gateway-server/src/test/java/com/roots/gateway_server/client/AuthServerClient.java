package com.roots.gateway_server.client;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Browser-style auth-server client for gateway integration tests.
 */
public class AuthServerClient implements AutoCloseable {

    private static final int MAX_REDIRECT_HOPS = 15;

    private final String baseUrl;
    private final HttpClient browser;
    private final Map<String, HttpCookie> browserCookies = new LinkedHashMap<>();

    public AuthServerClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.browser = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Plays the browser through guest login from an authorize URL until callback.
     */
    public String completeGuestLogin(String authorizeUrl, String callbackPrefix) throws Exception {
        followRedirects(get(authorizeUrl), callbackPrefix);

        HttpRequest.Builder guestLogin = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login/guest"))
                .POST(HttpRequest.BodyPublishers.noBody());
        HttpResponse<String> response = followRedirects(sendWithCookies(guestLogin), callbackPrefix);

        if (response.statusCode() != 302) {
            throw new IllegalStateException("Guest login did not reach callback; status " + response.statusCode());
        }
        return response.headers().firstValue("Location").orElseThrow();
    }

    @Override
    public void close() {
        browser.close();
    }

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
}
