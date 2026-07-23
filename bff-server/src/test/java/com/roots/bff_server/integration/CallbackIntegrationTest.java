package com.roots.bff_server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.roots.bff_server.client.AuthServerClient;
import com.roots.bff_server.client.BffClient;
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
import java.util.Base64;
import java.util.Map;

/**
 * Exercises GET /api/auth/callback against a live bff-server, its Redis, and a live
 * auth-server. The happy path is the full flow: /api/auth/authorize → guest login on
 * auth-server → the callback lands back on the bff with code+state → server-side
 * exchange → all three tokens in Redis → 302 to web-client "/" → /api/auth/status
 * flips to logged-in. Negatives assert the one-time state semantics and the generic
 * failure redirect: on every callback attempt the pending state is consumed, and any
 * failure (auth-server error, missing params, state mismatch, rejected code) lands
 * on web-client with ?e=login_failed and stores nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class CallbackIntegrationTest {

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
    void fullFlow_exchangesCodeStoresTokensAndRedirectsToWebClient() throws Exception {
        String location = startAuthorize();

        // Play the browser through a guest login; auth-server sends it back to the
        // bff callback with code + state.
        String callbackUrl = authServerClient.completeGuestLogin(location, callbackPrefix());
        HttpResponse<String> response = bffClient.getCallback(callbackUrl, sessionCookie);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow())
                .isEqualTo(webClientLocation + "/");

        // The server-side exchange stored the full token set, each with a real TTL.
        assertThat(tokenStore.getTimeToLive(sessionId, TokenType.ID_TOKEN)).isPositive();
        assertThat(tokenStore.getTimeToLive(sessionId, TokenType.ACCESS_TOKEN)).isPositive();
        assertThat(tokenStore.getTimeToLive(sessionId, TokenType.REFRESH_TOKEN)).isPositive();
        // The state is single-use: consumed by the callback.
        assertThat(tokenStore.find(sessionId, TokenType.OAUTH_STATE)).isEmpty();

        // The session is now a logged-in session as far as the status endpoint goes.
        HttpResponse<String> status = bffClient.getLoginStatus(sessionCookie);
        Map<String, Object> body = OBJECT_MAPPER.readValue(status.body(), new TypeReference<>() {
        });
        assertThat(body.get("isLoggedIn")).isEqualTo(true);
    }

    @Test
    void stateMismatch_redirectsWithErrorStoresNothingAndConsumesState() throws Exception {
        startAuthorize();

        HttpResponse<String> response = bffClient.getCallback(
                callbackPrefix() + "?code=irrelevant&state=not-the-minted-state", sessionCookie);

        assertFailureRedirect(response);
        assertNothingStored();
    }

    @Test
    void missingCode_redirectsWithErrorAndConsumesState() throws Exception {
        startAuthorize();
        String mintedState = tokenStore.find(sessionId, TokenType.OAUTH_STATE).orElseThrow();

        HttpResponse<String> response = bffClient.getCallback(
                callbackPrefix() + "?state=" + mintedState, sessionCookie);

        assertFailureRedirect(response);
        assertNothingStored();
    }

    @Test
    void rejectedCode_redirectsWithErrorStoresNothingAndConsumesState() throws Exception {
        startAuthorize();
        String mintedState = tokenStore.find(sessionId, TokenType.OAUTH_STATE).orElseThrow();

        // Correct state, bogus code: the state check passes and the exchange is
        // attempted — auth-server rejects it.
        HttpResponse<String> response = bffClient.getCallback(
                callbackPrefix() + "?code=not-a-real-code&state=" + mintedState, sessionCookie);

        assertFailureRedirect(response);
        assertNothingStored();
    }

    @Test
    void authServerError_redirectsWithErrorAndConsumesState() throws Exception {
        startAuthorize();

        HttpResponse<String> response = bffClient.getCallback(
                callbackPrefix() + "?error=access_denied", sessionCookie);

        assertFailureRedirect(response);
        assertNothingStored();
    }

    /** Kicks off the flow, remembers this session's cookie/id, returns the authorize URL. */
    private String startAuthorize() throws Exception {
        HttpResponse<String> response = bffClient.getAuthorize(null);
        assertThat(response.statusCode()).isEqualTo(302);

        sessionCookie = response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No SESSION cookie on authorize response"));
        sessionId = new String(
                Base64.getDecoder().decode(sessionCookie.substring("SESSION=".length())),
                StandardCharsets.UTF_8);

        return response.headers().firstValue("Location").orElseThrow();
    }

    private String callbackPrefix() {
        return bffServerLocation + "/api/auth/callback";
    }

    private void assertFailureRedirect(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow())
                .isEqualTo(webClientLocation + "/?e=login_failed");
    }

    private void assertNothingStored() {
        assertThat(tokenStore.find(sessionId, TokenType.ID_TOKEN)).isEmpty();
        assertThat(tokenStore.find(sessionId, TokenType.ACCESS_TOKEN)).isEmpty();
        assertThat(tokenStore.find(sessionId, TokenType.REFRESH_TOKEN)).isEmpty();
        assertThat(tokenStore.find(sessionId, TokenType.OAUTH_STATE)).isEmpty();
    }
}
