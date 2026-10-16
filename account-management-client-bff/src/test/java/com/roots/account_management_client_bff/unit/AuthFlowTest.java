package com.roots.account_management_client_bff.unit;

import com.roots.account_management_client_bff.client.AuthServerTokenClient;
import com.roots.account_management_client_bff.dto.response.TokenResponse;
import com.roots.account_management_client_bff.enums.TokenType;
import com.roots.account_management_client_bff.service.AuthCallbackService;
import com.roots.account_management_client_bff.service.AuthorizeService;
import com.roots.account_management_client_bff.service.LogoutService;
import com.roots.account_management_client_bff.service.TokenStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthFlowTest {

    private final TokenStoreService tokenStore = mock(TokenStoreService.class);
    private final AuthServerTokenClient tokenClient = mock(AuthServerTokenClient.class);
    private final AuthCallbackService callback = new AuthCallbackService(tokenStore, tokenClient);

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(callback, "accountManagementClientBffExternalLocation", "http://localhost:8084");
        ReflectionTestUtils.setField(callback, "accountManagementClientOrigin", "http://localhost:8084");
        when(tokenStore.find("session", TokenType.OAUTH_STATE)).thenReturn(Optional.of("state"));
        when(tokenStore.find("session", TokenType.OAUTH_CODE_VERIFIER)).thenReturn(Optional.of("verifier"));
        when(tokenStore.find("session", TokenType.OAUTH_NONCE)).thenReturn(Optional.of("nonce"));
    }

    @Test
    void authorizeUsesEmbeddedCallbackAndStoresPkceAndNonce() throws Exception {
        AuthorizeService authorize = new AuthorizeService(tokenStore);
        ReflectionTestUtils.setField(authorize, "authServerExternalLocation", "http://localhost:9000");
        ReflectionTestUtils.setField(authorize, "clientId", "ACCOUNT_MANAGEMENT_CLIENT");
        ReflectionTestUtils.setField(authorize, "accountManagementClientBffExternalLocation", "http://localhost:8084");

        URI redirect = authorize.buildAuthorizeRedirect("session");
        var query = UriComponentsBuilder.fromUri(redirect).build().getQueryParams();
        assertThat(redirect.getPath()).isEqualTo("/oauth2/authorize");
        assertThat(query.getFirst("client_id")).isEqualTo("ACCOUNT_MANAGEMENT_CLIENT");
        assertThat(query.getFirst("redirect_uri")).isEqualTo("http://localhost:8084/api/auth/callback");
        assertThat(query.getFirst("scope")).isEqualTo("openid%20ACCOUNT_MANAGEMENT_CLIENT");
        assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
        verify(tokenStore).store("session", TokenType.OAUTH_STATE, query.getFirst("state"), Duration.ofMinutes(5));
        verify(tokenStore).store("session", TokenType.OAUTH_NONCE, query.getFirst("nonce"), Duration.ofMinutes(5));

        var verifier = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(tokenStore).store(eq("session"), eq(TokenType.OAUTH_CODE_VERIFIER),
                verifier.capture(), eq(Duration.ofMinutes(5)));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getValue().getBytes(StandardCharsets.US_ASCII)));
        assertThat(query.getFirst("code_challenge")).isEqualTo(challenge);
    }

    @Test
    void callbackStoresTokensAndReturnsToEmbeddedHome() {
        TokenResponse tokens = new TokenResponse("access", "refresh", idToken("nonce"));
        when(tokenClient.exchangeAuthorizationCode("code", "http://localhost:8084/api/auth/callback", "verifier"))
                .thenReturn(Optional.of(tokens));

        assertThat(callback.handleCallback("session", "code", "state", null))
                .isEqualTo(URI.create("http://localhost:8084/"));
        verify(tokenStore).storeTokenResponse("session", tokens);
        assertFlowConsumed();
    }

    @Test
    void mismatchedStateReturnsToEmbeddedHomeWithErrorWithoutExchangingCode() {
        assertThat(callback.handleCallback("session", "code", "wrong", null))
                .isEqualTo(URI.create("http://localhost:8084/?e=login_failed"));
        verify(tokenClient, never()).exchangeAuthorizationCode(any(), any(), any());
        assertFlowConsumed();
    }

    @Test
    void mismatchedNonceDoesNotStoreTokens() {
        when(tokenClient.exchangeAuthorizationCode("code", "http://localhost:8084/api/auth/callback", "verifier"))
                .thenReturn(Optional.of(new TokenResponse("access", "refresh", idToken("wrong"))));

        assertThat(callback.handleCallback("session", "code", "state", null))
                .isEqualTo(URI.create("http://localhost:8084/?e=login_failed"));
        verify(tokenStore, never()).storeTokenResponse(any(), any());
        assertFlowConsumed();
    }

    @Test
    void logoutDefersTokenCleanupAndReturnsToEmbeddedLogout() {
        LogoutService logout = new LogoutService(tokenStore);
        ReflectionTestUtils.setField(logout, "authServerExternalLocation", "http://localhost:9000");
        ReflectionTestUtils.setField(logout, "clientId", "ACCOUNT_MANAGEMENT_CLIENT");
        ReflectionTestUtils.setField(logout, "accountManagementClientOrigin", "http://localhost:8084");
        when(tokenStore.find("session", TokenType.ID_TOKEN)).thenReturn(Optional.of("id-token"));

        URI redirect = logout.buildLogoutRedirect("session");
        var query = UriComponentsBuilder.fromUri(redirect).build().getQueryParams();
        assertThat(redirect.getPath()).isEqualTo("/connect/logout");
        assertThat(query.getFirst("post_logout_redirect_uri")).isEqualTo("http://localhost:8084/logout");
        assertThat(query.getFirst("client_id")).isEqualTo("ACCOUNT_MANAGEMENT_CLIENT");
        assertThat(query.getFirst("id_token_hint")).isEqualTo("id-token");
        verify(tokenStore, never()).clearTokens(any());
    }

    private void assertFlowConsumed() {
        verify(tokenStore).delete("session", TokenType.OAUTH_STATE);
        verify(tokenStore).delete("session", TokenType.OAUTH_CODE_VERIFIER);
        verify(tokenStore).delete("session", TokenType.OAUTH_NONCE);
    }

    private static String idToken(String nonce) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"nonce\":\"" + nonce + "\"}").getBytes(StandardCharsets.UTF_8));
        return "eyJhbGciOiJSUzI1NiJ9." + payload + ".signature";
    }
}
