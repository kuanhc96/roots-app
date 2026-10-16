package com.roots.account_management_client_bff.unit;

import com.roots.account_management_client_bff.controller.AuthController;
import com.roots.account_management_client_bff.enums.TokenType;
import com.roots.account_management_client_bff.service.LogoutService;
import com.roots.account_management_client_bff.service.TokenStoreService;
import com.roots.account_management_client_bff.validator.LogoutTokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BackChannelLogoutTest {

    private final TokenStoreService tokenStore = mock(TokenStoreService.class);
    private final LogoutService logout = new LogoutService(tokenStore);
    private final LogoutTokenValidator validator = new LogoutTokenValidator(tokenStore);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(validator, "authServerInternalLocation", "http://localhost:9000");
        ReflectionTestUtils.setField(validator, "clientId", "ACCOUNT_MANAGEMENT_CLIENT");
        when(tokenStore.findSessionIdBySid("sid")).thenReturn(Optional.of("session"));
        when(tokenStore.find("session", TokenType.ID_TOKEN)).thenReturn(Optional.of(jwt(claims())));
        mvc = MockMvcBuilders.standaloneSetup(
                new AuthController(null, null, null, logout, validator)).build();
    }

    @Test
    void notificationRecordsReplayTokenThenClearsMappedTokensWithoutBrowserSession() throws Exception {
        String token = jwt(claims());
        mvc.perform(post("/api/auth/logout/back-channel/test")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", token))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
        var order = inOrder(tokenStore);
        order.verify(tokenStore).storeLogoutToken(token);
        order.verify(tokenStore).clearTokensBySid("sid");
    }

    @Test
    void missingLogoutTokenReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/auth/logout/back-channel/test"))
                .andExpect(status().isBadRequest());
        verify(tokenStore, never()).clearTokensBySid(anyString());
    }

    @Test
    void replayReturnsBadRequestWithoutClearingTokens() throws Exception {
        when(tokenStore.logoutTokenExists("jti")).thenReturn(true);
        mvc.perform(post("/api/auth/logout/back-channel/test").param("logout_token", jwt(claims())))
                .andExpect(status().isBadRequest());
        verify(tokenStore, never()).clearTokensBySid(anyString());
    }

    @Test
    void unknownSidReturnsNotFound() throws Exception {
        when(tokenStore.findSessionIdBySid("sid")).thenReturn(Optional.empty());
        mvc.perform(post("/api/auth/logout/back-channel/test").param("logout_token", jwt(claims())))
                .andExpect(status().isNotFound());
        verify(tokenStore, never()).storeLogoutToken(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"alg", "iss", "aud", "iat", "exp", "sub", "sid", "nonce", "events"})
    void invalidClaimsAreRejected(String invalidClaim) throws Exception {
        Map<String, Object> claims = claims();
        String alg = "RS256";
        switch (invalidClaim) {
            case "alg" -> alg = "none";
            case "iss" -> claims.put("iss", "http://wrong-issuer");
            case "aud" -> claims.put("aud", "WEB_CLIENT_PKCE");
            case "iat" -> claims.put("iat", Instant.now().plusSeconds(60).getEpochSecond());
            case "exp" -> claims.put("exp", Instant.now().minusSeconds(60).getEpochSecond());
            case "sub", "sid" -> claims.remove(invalidClaim);
            case "nonce" -> claims.put("nonce", "unexpected");
            case "events" -> claims.put("events", Map.of());
            default -> throw new IllegalArgumentException(invalidClaim);
        }
        mvc.perform(post("/api/auth/logout/back-channel/test")
                        .param("logout_token", jwt(claims, alg)))
                .andExpect(status().isBadRequest());
        verify(tokenStore, never()).storeLogoutToken(anyString());
        verify(tokenStore, never()).clearTokensBySid(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "sub", "sid"})
    void notificationMustMatchStoredIdToken(String claim) {
        Map<String, Object> stored = claims();
        stored.put(claim, "different");
        when(tokenStore.find("session", TokenType.ID_TOKEN)).thenReturn(Optional.of(jwt(stored)));
        assertThatThrownBy(() -> validator.validate(jwt(claims())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void mappedSessionWithoutIdTokenStillAcceptsNotification() throws Exception {
        when(tokenStore.find("session", TokenType.ID_TOKEN)).thenReturn(Optional.empty());
        mvc.perform(post("/api/auth/logout/back-channel/test").param("logout_token", jwt(claims())))
                .andExpect(status().isOk());
        verify(tokenStore).clearTokensBySid("sid");
    }

    static Map<String, Object> claims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "http://localhost:9000");
        claims.put("aud", "ACCOUNT_MANAGEMENT_CLIENT");
        claims.put("iat", Instant.now().minusSeconds(10).getEpochSecond());
        claims.put("exp", Instant.now().plusSeconds(300).getEpochSecond());
        claims.put("sub", "user@example.com");
        claims.put("sid", "sid");
        claims.put("jti", "jti");
        claims.put("events", Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()));
        return claims;
    }

    static String jwt(Map<String, Object> claims) {
        return jwt(claims, "RS256");
    }

    private static String jwt(Map<String, Object> claims, String alg) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        ObjectMapper mapper = new ObjectMapper();
        return encoder.encodeToString(mapper.writeValueAsString(Map.of("alg", alg)).getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(mapper.writeValueAsString(claims).getBytes(StandardCharsets.UTF_8))
                + ".signature";
    }
}
