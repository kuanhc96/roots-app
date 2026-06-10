package com.roots.authserver.integration;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Static helpers shared across the integration test suites for navigating the
 * auth-server's HTTP redirect flows (resolving Location headers and reading query
 * parameters off the resulting callback URLs).
 */
public final class HttpFlowUtils {

    private HttpFlowUtils() {
    }

    /**
     * Resolves a {@code Location} header value: returned as-is when already absolute,
     * otherwise prefixed with {@code baseUrl}.
     */
    public static String resolveLocation(String baseUrl, String location) {
        return location.startsWith("http") ? location : baseUrl + location;
    }

    /**
     * Extracts the first value of the named query parameter from {@code url}, throwing
     * if it is absent.
     */
    public static String extractQueryParam(String url, String name) {
        String query = URI.create(url).getQuery();
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv[0].equals(name) && kv.length == 2) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Parameter '" + name + "' not found in: " + url);
    }
}
