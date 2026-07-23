package com.roots.bff_server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.roots.bff_server.client.AuthServerClient;
import com.roots.bff_server.client.BffClient;
import com.roots.bff_server.enums.TokenType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Exercises GET /api/auth/authorize against a live bff-server, its Redis, and a live
 * auth-server. The contract test asserts the raw 302 — every query parameter of the
 * authorize URL, and that the minted {@code state} sits in Redis under the session
 * with a real TTL (read via the autowired {@link TestTokenStoreService}). The
 * acceptance test then actually follows that Location through a guest login, proving
 * auth-server accepts the bff-built URL end-to-end: the web-client callback receives
 * a {@code code} and echoes exactly the bff-held state.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class AuthorizeIntegrationTest {

    @Value("${bff-server-location}")
    private String bffServerLocation;

    @Value("${auth-server-location}")
    private String authServerLocation;

    @Value("${web-client-location}")
    private String webClientLocation;

    @Value("${web-client-id}")
    private String webClientId;

    @Value("${web-client-secret}")
    private String webClientSecret;

    @Autowired
    private TestTokenStoreService tokenStore;

    private BffClient bffClient;
    private AuthServerClient authServerClient;
    private String sessionId;

    @BeforeEach
    void setUp() {
        bffClient = new BffClient(bffServerLocation);
        authServerClient = new AuthServerClient(authServerLocation, webClientLocation, webClientId, webClientSecret);
    }

    @AfterEach
    void tearDown() {
        if (sessionId != null) {
            tokenStore.deleteAll(sessionId);
        }
        bffClient.close();
        authServerClient.close();
    }

    @Test
    void authorize_redirectsToAuthServerAndHoldsStateInRedis() throws Exception {
        HttpResponse<String> response = bffClient.getAuthorize(null);

        assertThat(response.statusCode()).isEqualTo(302);
        rememberSessionId(response);

        String location = response.headers().firstValue("Location").orElseThrow();
        assertThat(location).startsWith(authServerLocation + "/oauth2/authorize?");
        assertThat(AuthServerClient.queryParam(location, "response_type")).isEqualTo("code");
        assertThat(AuthServerClient.queryParam(location, "client_id")).isEqualTo(webClientId);
        assertThat(AuthServerClient.queryParam(location, "redirect_uri"))
                .isEqualTo(webClientLocation + "/callback");
        assertThat(AuthServerClient.queryParam(location, "scope")).isEqualTo("openid WEB_CLIENT_READ");

        // The state in the URL is exactly the one held for this session, with a real
        // TTL — the future bff callback validates against this key.
        String state = AuthServerClient.queryParam(location, "state");
        assertThat(state).isNotBlank();
        assertThat(tokenStore.find(sessionId, TokenType.OAUTH_STATE)).contains(state);
        assertThat(tokenStore.getTimeToLive(sessionId, TokenType.OAUTH_STATE)).isPositive();
    }

    @Test
    void authorizeRedirect_isAcceptedByAuthServer_callbackCarriesCodeAndHeldState() throws Exception {
        HttpResponse<String> response = bffClient.getAuthorize(null);
        assertThat(response.statusCode()).isEqualTo(302);
        rememberSessionId(response);
        String location = response.headers().firstValue("Location").orElseThrow();

        // Play the browser: follow the bff-built authorize URL through a guest login.
        String callback = authServerClient.completeGuestLogin(location);

        assertThat(callback).startsWith(webClientLocation + "/callback");
        assertThat(AuthServerClient.queryParam(callback, "code")).isNotBlank();
        assertThat(AuthServerClient.queryParam(callback, "state"))
                .isEqualTo(tokenStore.find(sessionId, TokenType.OAUTH_STATE).orElseThrow());
    }

    /** The SESSION cookie on the response is base64(sessionId) — the Redis key prefix. */
    private void rememberSessionId(HttpResponse<String> response) {
        String cookie = response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No SESSION cookie on authorize response"));
        sessionId = new String(
                Base64.getDecoder().decode(cookie.substring("SESSION=".length())),
                StandardCharsets.UTF_8);
    }
}
