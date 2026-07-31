package com.roots.bff_server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.roots.bff_server.client.AuthServerClient;
import com.roots.bff_server.client.BffClient;
import com.roots.bff_server.dto.response.TokenResponse;
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
import java.time.Duration;
import java.util.Base64;

/**
 * Exercises GET /api/auth/logout against a live bff-server, its Redis, and a live
 * auth-server. Genuine tokens come from a real guest login
 * ({@link AuthServerClient#fetchGuestTokens()}) and are seeded into Redis via the
 * autowired {@link TestTokenStoreService} under the test's own session id (the __Host-SESSION
 * cookie is base64 of the Spring Session id — the Redis key prefix).
 *
 * <p>The contract tests assert the raw 302: the auth-server {@code /connect/logout}
 * URL with {@code client_id} + {@code post_logout_redirect_uri} (and
 * {@code id_token_hint} only when an id_token is held), and that the session's token
 * keys are gone afterward. The acceptance test follows that Location on the same
 * cookie-bearing browser used for the guest login, proving auth-server accepts the
 * bff-built logout URL end-to-end and lands the browser on web-client's /logout.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class LogoutIntegrationTest {

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

        // First contact establishes the session; its cookie is base64(sessionId), the
        // Redis key prefix the bff clears at logout.
        HttpResponse<String> response = bffClient.getLoginStatus(null);
        sessionCookie = response.headers().allValues("set-cookie").stream()
                .filter(cookie -> cookie.startsWith("__Host-SESSION="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No __Host-SESSION cookie on first response"));
        sessionId = new String(
                Base64.getDecoder().decode(sessionCookie.substring("__Host-SESSION=".length())),
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
    void logout_clearsTokensAndRedirectsToConnectLogoutWithHint() throws Exception {
        TokenResponse tokens = authServerClient.fetchGuestTokens();
        seedAllTokens(tokens);

        HttpResponse<String> response = bffClient.getLogout(sessionCookie);

        assertThat(response.statusCode()).isEqualTo(302);
        String location = response.headers().firstValue("Location").orElseThrow();
        assertThat(location).startsWith(authServerLocation + "/connect/logout?");
        assertThat(AuthServerClient.queryParam(location, "client_id")).isEqualTo(webClientId);
        assertThat(AuthServerClient.queryParam(location, "post_logout_redirect_uri"))
                .isEqualTo(webClientLocation + "/logout");
        // The bff supplies the id_token_hint the browser can't — it never holds the id_token.
        assertThat(AuthServerClient.queryParam(location, "id_token_hint")).isEqualTo(tokens.idToken());

        // All three token keys are cleared for the session.
        assertThat(tokenStore.find(sessionId, TokenType.ID_TOKEN)).isEmpty();
        assertThat(tokenStore.find(sessionId, TokenType.ACCESS_TOKEN)).isEmpty();
        assertThat(tokenStore.find(sessionId, TokenType.REFRESH_TOKEN)).isEmpty();
    }

    @Test
    void logout_withoutIdToken_omitsHintButStillRedirects() throws Exception {
        // A login revived via refresh-only never has an id_token stored: no hint to send,
        // but client_id still lets auth-server honour the post_logout_redirect_uri.
        TokenResponse tokens = authServerClient.fetchGuestTokens();
        tokenStore.store(sessionId, TokenType.REFRESH_TOKEN, tokens.refreshToken(), Duration.ofMinutes(5));

        HttpResponse<String> response = bffClient.getLogout(sessionCookie);

        assertThat(response.statusCode()).isEqualTo(302);
        String location = response.headers().firstValue("Location").orElseThrow();
        assertThat(location).startsWith(authServerLocation + "/connect/logout?");
        assertThat(location).doesNotContain("id_token_hint");
        assertThat(AuthServerClient.queryParam(location, "client_id")).isEqualTo(webClientId);
        assertThat(AuthServerClient.queryParam(location, "post_logout_redirect_uri"))
                .isEqualTo(webClientLocation + "/logout");

        assertThat(tokenStore.find(sessionId, TokenType.REFRESH_TOKEN)).isEmpty();
    }

    @Test
    void logoutRedirect_isAcceptedByAuthServer_landsOnWebClientLogout() throws Exception {
        // fetchGuestTokens leaves the AuthServerClient browser holding auth-server's
        // session cookie, so replaying the logout URL on it exercises the real
        // "browser that logged in now logs out" path.
        TokenResponse tokens = authServerClient.fetchGuestTokens();
        tokenStore.store(sessionId, TokenType.ID_TOKEN, tokens.idToken(), Duration.ofMinutes(5));

        HttpResponse<String> response = bffClient.getLogout(sessionCookie);
        assertThat(response.statusCode()).isEqualTo(302);
        String logoutUrl = response.headers().firstValue("Location").orElseThrow();

        // Session present AND valid id_token_hint → Spring's OIDC logout skips the
        // confirmation page and 302s straight to the post_logout_redirect_uri.
        String postLogoutPrefix = webClientLocation + "/logout";
        String finalLocation = authServerClient.completeLogout(logoutUrl, postLogoutPrefix);

        assertThat(finalLocation).startsWith(postLogoutPrefix);
    }

    private void seedAllTokens(TokenResponse tokens) {
        tokenStore.store(sessionId, TokenType.ID_TOKEN, tokens.idToken(), Duration.ofMinutes(5));
        tokenStore.store(sessionId, TokenType.ACCESS_TOKEN, tokens.accessToken(), Duration.ofMinutes(5));
        tokenStore.store(sessionId, TokenType.REFRESH_TOKEN, tokens.refreshToken(), Duration.ofMinutes(5));
    }
}
