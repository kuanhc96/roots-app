package com.roots.web_client_bff.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.HttpCookie;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Live-server tests: start web-client-bff, auth-server, gateway and simple-resource-server first. */
class OAuth2IntegrationTest {
    private static final String BFF = System.getProperty("bff-server-location", "http://localhost:8083");
    private static final String AUTH = System.getProperty("auth-server-location", "http://localhost:9000");
    private static final String CALLBACK = BFF + "/login/oauth2/code/web-client-registration";
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final ObjectMapper json = new ObjectMapper();
    private HttpClient client;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Test
    void guestLoginCallbackRoleAndLogout() throws Exception {
        HttpResponse<String> unauthenticated = get(BFF + "/api/role/guest");
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(status().get("isLoggedIn").asBoolean()).isFalse();

        HttpResponse<String> start = get(BFF + "/oauth2/authorization/web-client-registration");
        assertThat(start.statusCode()).isEqualTo(302);
        String oldSession = cookies.get("__Host-SESSION");
        assertThat(oldSession).isNotBlank();
        String authorization = location(start);
        assertThat(query(authorization, "client_id")).isEqualTo("WEB_CLIENT_PKCE");
        assertThat(query(authorization, "redirect_uri")).isEqualTo(CALLBACK);
        assertThat(query(authorization, "code_challenge_method")).isEqualTo("S256");
        assertThat(query(authorization, "code_challenge")).isNotBlank();
        assertThat(query(authorization, "state")).isNotBlank();

        HttpResponse<String> login = followUntil(get(authorization), CALLBACK);
        assertThat(login.statusCode()).isEqualTo(200);
        HttpResponse<String> result = followUntil(post(AUTH + "/login/guest"), CALLBACK);
        assertThat(result.statusCode()).isEqualTo(302);
        String callback = location(result);
        assertThat(query(callback, "state")).isEqualTo(query(authorization, "state"));
        HttpResponse<String> callbackResponse = get(callback);
        assertThat(callbackResponse.statusCode()).isEqualTo(302);
        assertThat(location(callbackResponse)).isEqualTo(BFF + "/");
        assertThat(cookies.get("__Host-SESSION")).isNotBlank().isNotEqualTo(oldSession);

        JsonNode claims = status();
        assertThat(claims.get("isLoggedIn").asBoolean()).isTrue();
        assertThat(claims.get("email").asString()).isEqualTo("guest");
        assertThat(claims.hasNonNull("userGUID")).isFalse();
        assertThat(claims.get("roles").toString()).contains("GUEST");
        HttpResponse<String> guest = get(BFF + "/api/role/guest");
        assertThat(guest.statusCode()).isEqualTo(200);
        assertThat(guest.body()).isEqualTo("I am a guest");
        assertThat(get(BFF + "/api/role/pastor").statusCode()).isEqualTo(403);

        String loggedInSession = cookies.get("__Host-SESSION");
        HttpResponse<String> logout = get(BFF + "/api/auth/logout");
        assertThat(logout.statusCode()).isEqualTo(302);
        String logoutUrl = location(logout);
        assertThat(logoutUrl).startsWith(AUTH + "/connect/logout?");
        assertThat(query(logoutUrl, "client_id")).isEqualTo("WEB_CLIENT_PKCE");
        assertThat(query(logoutUrl, "post_logout_redirect_uri")).isEqualTo(BFF + "/logout");
        assertThat(query(logoutUrl, "id_token_hint")).isNotBlank();
        assertThat(cookies.get("__Host-SESSION")).isNull();
        HttpResponse<String> end = followUntil(get(logoutUrl), BFF + "/logout");
        assertThat(location(end)).startsWith(BFF + "/logout");
        cookies.put("__Host-SESSION", loggedInSession);
        assertThat(status().get("isLoggedIn").asBoolean()).isFalse();
        assertThat(get(BFF + "/api/role/guest").statusCode()).isEqualTo(401);
    }

    @Test
    void incorrectStateFailsAndRetiredRoutesDoNotStartOAuth() throws Exception {
        HttpResponse<String> start = get(BFF + "/oauth2/authorization/web-client-registration");
        assertThat(start.statusCode()).isEqualTo(302);
        HttpResponse<String> failed = get(CALLBACK + "?code=irrelevant&state=wrong");
        assertThat(failed.statusCode()).isEqualTo(302);
        assertThat(location(failed)).isEqualTo(BFF + "/?e=login_failed");
        assertThat(status().get("isLoggedIn").asBoolean()).isFalse();
        for (String path : new String[]{"/api/auth/authorize", "/api/auth/callback?code=x&state=y"}) {
            HttpResponse<String> retired = get(BFF + path);
            assertThat(retired.statusCode()).isNotEqualTo(302);
        }

    }

    @Test
    void authServerBackChannelLogoutInvalidatesOtherBrowserSession() throws Exception {
        HttpResponse<String> start = get(BFF + "/oauth2/authorization/web-client-registration");
        followUntil(get(location(start)), CALLBACK);
        HttpResponse<String> login = followUntil(post(AUTH + "/login/guest"), CALLBACK);
        assertThat(get(location(login)).statusCode()).isEqualTo(302);
        String firstSession = cookies.get("__Host-SESSION");

        cookies.remove("__Host-SESSION");
        HttpResponse<String> secondStart = get(BFF + "/oauth2/authorization/web-client-registration");
        HttpResponse<String> secondLogin = followUntil(get(location(secondStart)), CALLBACK);
        assertThat(location(secondLogin)).startsWith(CALLBACK);
        assertThat(get(location(secondLogin)).statusCode()).isEqualTo(302);
        String secondSession = cookies.get("__Host-SESSION");
        assertThat(secondSession).isNotEqualTo(firstSession);
        assertThat(status().get("isLoggedIn").asBoolean()).isTrue();

        cookies.put("__Host-SESSION", firstSession);
        HttpResponse<String> logout = get(BFF + "/api/auth/logout");
        HttpResponse<String> end = followUntil(get(location(logout)), BFF + "/logout");
        assertThat(location(end)).startsWith(BFF + "/logout");

        cookies.put("__Host-SESSION", secondSession);
        assertThat(status().get("isLoggedIn").asBoolean()).isFalse();
    }

    private JsonNode status() throws Exception {
        HttpResponse<String> response = get(BFF + "/api/auth/status");
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private HttpResponse<String> followUntil(HttpResponse<String> response, String target) throws Exception {
        for (int i = 0; i < 15 && response.statusCode() == 302; i++) {
            String next = response.uri().resolve(location(response)).toString();
            if (next.startsWith(target)) return response;
            response = get(next);
        }
        return response;
    }

    private HttpResponse<String> get(String url) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(url)).GET());
    }

    private HttpResponse<String> post(String url) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()));
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        for (String header : response.headers().allValues("Set-Cookie")) {
            for (HttpCookie cookie : HttpCookie.parse(header)) {
                if (cookie.getMaxAge() == 0) cookies.remove(cookie.getName());
                else cookies.put(cookie.getName(), cookie.getValue());
            }
        }
        return response;
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElseThrow();
    }

    private static String query(String url, String name) {
        for (String part : URI.create(url).getRawQuery().split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name))
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
        }
        throw new AssertionError("Missing query parameter " + name);
    }
}
