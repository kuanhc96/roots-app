package com.roots.bff_server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Smoke test against a live bff-server (localhost:8083) and its Redis: proves the app is
 * healthy, that {@code SessionCreationPolicy.ALWAYS} issues a session on any request, and
 * that the session is recognized on a follow-up request (i.e. it round-trips through
 * Spring Session's Redis store rather than dying with the request).
 *
 * <p>The client is built fresh per test and closed afterwards (per-test lifecycle, same
 * rationale as auth-server's integration suite). No cookie handler is installed — cookies
 * are asserted and replayed explicitly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:/application.yml")
class SessionSmokeIntegrationTest {

    private static final String SESSION_COOKIE = "SESSION";

    @Value("${bff-server-location}")
    private String bffServerLocation;

    private HttpClient httpClient;

    @BeforeEach
    void buildClient() {
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void closeClient() {
        httpClient.close();
    }

    @Test
    void healthIsUpAndSessionIsCreatedAndPersisted() throws Exception {
        HttpResponse<String> first = httpClient.send(
                healthRequest().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("UP");

        // SessionCreationPolicy.ALWAYS: even an anonymous health probe gets a session.
        Optional<String> sessionCookie = extractSessionCookie(first);
        assertThat(sessionCookie)
                .as("first response should set a %s cookie", SESSION_COOKIE)
                .isPresent();

        // Replaying the cookie must not mint a new session — the first one was found in Redis.
        HttpResponse<String> second = httpClient.send(
                healthRequest().header("Cookie", sessionCookie.get()).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(extractSessionCookie(second))
                .as("second response should not set a new %s cookie", SESSION_COOKIE)
                .isEmpty();
    }

    private HttpRequest.Builder healthRequest() {
        return HttpRequest.newBuilder(URI.create(bffServerLocation + "/actuator/health")).GET();
    }

    /** Returns the {@code SESSION=<value>} pair from Set-Cookie, if present. */
    private static Optional<String> extractSessionCookie(HttpResponse<?> response) {
        List<String> setCookies = response.headers().allValues("set-cookie");
        return setCookies.stream()
                .filter(cookie -> cookie.startsWith(SESSION_COOKIE + "="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst();
    }
}
