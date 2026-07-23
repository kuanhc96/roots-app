package com.roots.bff_server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.roots.bff_server.client.AuthServerClient;
import com.roots.bff_server.client.BffClient;
import com.roots.bff_server.dto.response.TokenResponse;
import com.roots.bff_server.enums.TokenType;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

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
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Exercises GET /api/auth/status against a live bff-server, its Redis, and a live
 * auth-server. Genuine tokens come from a real guest login
 * ({@link AuthServerClient#fetchGuestTokens()}); they are seeded into Redis via the
 * autowired {@link TestTokenStoreService} under the test's own session id — the
 * SESSION cookie is just the base64 of the Spring Session id, so the test can derive
 * the key prefix from its own cookie. Covers all four paths: id_token hit,
 * refresh-token revival (with rotation), nothing stored, and a refresh token
 * auth-server rejects.
 *
 * <p>HTTP clients are built fresh per test and closed afterwards (per-test lifecycle,
 * same rationale as the other integration suites); the Redis-backed token store is a
 * suite-wide bean from {@link TestConfig}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class AuthStatusIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    private String sessionCookie;

    @BeforeEach
    void setUp() throws Exception {
        bffClient = new BffClient(bffServerLocation);
        authServerClient = new AuthServerClient(authServerLocation, webClientLocation, webClientId, webClientSecret);

        // First contact establishes the session; its cookie is base64(sessionId), which
        // is the Redis key prefix the bff will look under for this browser.
        HttpResponse<String> response = bffClient.getLoginStatus(null);
        sessionCookie = response.headers().allValues("set-cookie").stream()
                .filter(cookie -> cookie.startsWith("SESSION="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No SESSION cookie on first response"));
        sessionId = new String(
                Base64.getDecoder().decode(sessionCookie.substring("SESSION=".length())),
                StandardCharsets.UTF_8);
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
    void noStoredTokens_reportsNotLoggedIn() throws Exception {
        Map<String, Object> body = statusBody();

        assertThat(body.get("isLoggedIn")).isEqualTo(false);
        // NON_NULL serialization: a logged-out answer carries no claim fields at all.
        assertThat(body).doesNotContainKeys("email", "userGUID", "roles");
    }

    @Test
    void storedIdToken_reportsLoggedInWithClaims() throws Exception {
        TokenResponse tokens = authServerClient.fetchGuestTokens();
        tokenStore.store(sessionId, TokenType.ID_TOKEN, tokens.idToken(), Duration.ofMinutes(5));

        Map<String, Object> body = statusBody();

        assertThat(body.get("isLoggedIn")).isEqualTo(true);
        assertThat(body.get("email")).isEqualTo("guest");
        // The guest principal has no user_credential row, so no userGUID claim exists.
        assertThat(body).doesNotContainKey("userGUID");
        assertThat(asStringList(body.get("roles"))).contains("GUEST");
    }

    @Test
    void refreshTokenOnly_refreshesRotatesAndStoresTokens() throws Exception {
        TokenResponse tokens = authServerClient.fetchGuestTokens();
        tokenStore.store(sessionId, TokenType.REFRESH_TOKEN, tokens.refreshToken(), Duration.ofMinutes(5));

        Map<String, Object> body = statusBody();

        assertThat(body.get("isLoggedIn")).isEqualTo(true);
        assertThat(body.get("email")).isEqualTo("guest");
        assertThat(asStringList(body.get("roles"))).contains("GUEST");

        // The exchange stored fresh id/access tokens, each with a real TTL from its exp.
        assertThat(tokenStore.getTimeToLive(sessionId, TokenType.ID_TOKEN)).isPositive();
        assertThat(tokenStore.getTimeToLive(sessionId, TokenType.ACCESS_TOKEN)).isPositive();

        // reuse-refresh-tokens=false: the stored refresh token must be a rotated one.
        String storedRefreshToken = tokenStore.find(sessionId, TokenType.REFRESH_TOKEN).orElseThrow();
        assertThat(storedRefreshToken).isNotEqualTo(tokens.refreshToken());
    }

    @Test
    void rejectedRefreshToken_reportsNotLoggedInAndDeletesIt() throws Exception {
        tokenStore.store(sessionId, TokenType.REFRESH_TOKEN, "not-a-real-refresh-token", Duration.ofMinutes(5));

        Map<String, Object> body = statusBody();

        assertThat(body.get("isLoggedIn")).isEqualTo(false);
        assertThat(tokenStore.find(sessionId, TokenType.REFRESH_TOKEN)).isEmpty();
    }

    private Map<String, Object> statusBody() throws Exception {
        HttpResponse<String> response = bffClient.getLoginStatus(sessionCookie);
        assertThat(response.statusCode()).isEqualTo(200);
        return OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {
        });
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        return (List<String>) value;
    }
}
