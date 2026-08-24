package com.roots.gateway_server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.roots.gateway_server.client.AuthServerClient;
import com.roots.gateway_server.client.GatewayClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@ExtendWith(SpringExtension.class)
@TestPropertySource("classpath:/application.yml")
class GuestRoleGatewayIntegrationTest {

    @Value("${gateway-server-location}")
    private String gatewayServerLocation;

    @Value("${auth-server-location}")
    private String authServerLocation;

    @Value("${bff-server-location}")
    private String bffServerLocation;

    private GatewayClient gatewayClient;
    private AuthServerClient authServerClient;

    @BeforeEach
    void setUp() {
        gatewayClient = new GatewayClient(gatewayServerLocation);
        authServerClient = new AuthServerClient(authServerLocation);
    }

    @AfterEach
    void tearDown() {
        gatewayClient.close();
        authServerClient.close();
    }

    @Test
    void guestLoginFlow_allThroughGateway_allowsGuestEndpointAccess() throws Exception {
        HttpResponse<String> statusBefore = gatewayClient.getLoginStatus(null);
        assertThat(statusBefore.statusCode()).isEqualTo(200);
        assertThat(statusBefore.body()).contains("\"isLoggedIn\":false");

        String sessionCookie = extractSessionCookie(statusBefore)
                .orElseThrow(() -> new IllegalStateException("No __Host-SESSION cookie on status response"));

        HttpResponse<String> authorizeResponse = gatewayClient.getAuthorize(sessionCookie);
        // getAuthorize follows the bff-server 302 to auth-server's /oauth2/authorize;
        // that endpoint in turn redirects to /login (saving the request), so we assert
        // on the URI of the request that produced this response — the authorize URL itself.
        String authorizeLocation = authorizeResponse.uri().toString();
        assertThat(authorizeLocation).startsWith(authServerLocation + "/oauth2/authorize?");

        String callbackPrefix = bffServerLocation + "/api/auth/callback";
        String callbackUrl = authServerClient.completeGuestLogin(authorizeLocation, callbackPrefix);
        assertThat(callbackUrl).startsWith(callbackPrefix);
        assertThat(queryParam(callbackUrl, "code")).isNotBlank();

        HttpResponse<String> callbackResponse = gatewayClient.getCallback(callbackUrl, sessionCookie);
        assertThat(callbackResponse.statusCode()).isEqualTo(302);
        sessionCookie = extractSessionCookie(callbackResponse).orElse(sessionCookie);

        HttpResponse<String> statusAfter = gatewayClient.getLoginStatus(sessionCookie);
        assertThat(statusAfter.statusCode()).isEqualTo(200);
        assertThat(statusAfter.body()).contains("\"isLoggedIn\":true");

        HttpResponse<String> guestRoleResponse = gatewayClient.getGuestRole(sessionCookie);
        assertThat(guestRoleResponse.statusCode()).isEqualTo(200);
        assertThat(guestRoleResponse.body()).isEqualTo("I am a guest");
    }

    private static Optional<String> extractSessionCookie(HttpResponse<String> response) {
        return response.headers().allValues("set-cookie").stream()
                .filter(cookie -> cookie.startsWith("__Host-SESSION="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst();
    }

    private static String queryParam(String url, String name) {
        String rawQuery = URI.create(url).getRawQuery();
        if (rawQuery == null) {
            throw new IllegalArgumentException("No query string in URL: " + url);
        }

        for (String param : rawQuery.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv[0].equals(name) && kv.length == 2) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Parameter '" + name + "' not found in: " + url);
    }
}
