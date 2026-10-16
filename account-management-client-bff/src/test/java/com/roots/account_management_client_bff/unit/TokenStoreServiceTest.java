package com.roots.account_management_client_bff.unit;

import com.roots.account_management_client_bff.dto.response.TokenResponse;
import com.roots.account_management_client_bff.enums.TokenType;
import com.roots.account_management_client_bff.service.TokenStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenStoreServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final TokenStoreService store = new TokenStoreService(redis);

    @BeforeEach
    void setup() {
        when(redis.opsForValue()).thenReturn(values);
        ReflectionTestUtils.setField(store, "clientId", "ACCOUNT_MANAGEMENT_CLIENT");
        ReflectionTestUtils.setField(store, "refreshTokenTtlSeconds", 3600L);
    }

    @Test
    void tokenResponseStoresClientScopedSidMappingWithIdTokenTtl() {
        String idToken = BackChannelLogoutTest.jwt(BackChannelLogoutTest.claims());
        store.storeTokenResponse("session", new TokenResponse(idToken, "refresh", idToken));
        var ttl = org.mockito.ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq("oidc:ACCOUNT_MANAGEMENT_CLIENT:sid:sid"), eq("session"), ttl.capture());
        assertThat(ttl.getValue()).isBetween(Duration.ofSeconds(295), Duration.ofSeconds(300));
        verify(values).set("session:refresh_token", "refresh", Duration.ofSeconds(3600));
        verify(values).set(eq("session:id_token"), eq(idToken), any(Duration.class));
        verify(values).set(eq("session:access_token"), eq(idToken), any(Duration.class));
    }

    @Test
    void refreshedResponseUpdatesSidMapping() {
        Map<String, Object> claims = BackChannelLogoutTest.claims();
        claims.put("sid", "refreshed-sid");
        String token = BackChannelLogoutTest.jwt(claims);
        store.storeTokenResponse("session", new TokenResponse(token, "rotated", token));
        verify(values).set(eq("oidc:ACCOUNT_MANAGEMENT_CLIENT:sid:refreshed-sid"),
                eq("session"), any(Duration.class));
    }

    @Test
    void responseWithoutIdTokenPreservesExistingMappingAndDeletesAbsentRefreshToken() {
        store.storeTokenResponse("session", new TokenResponse(null, null, null));
        verify(values, never()).set(any(String.class), any(String.class), any(Duration.class));
        verify(redis).delete("session:refresh_token");
    }

    @Test
    void expiredIdTokenDoesNotStoreMapping() {
        Map<String, Object> claims = BackChannelLogoutTest.claims();
        claims.put("exp", Instant.now().minusSeconds(1).getEpochSecond());
        store.storeTokenResponse("session", new TokenResponse(null, null, BackChannelLogoutTest.jwt(claims)));
        verify(values, never()).set(any(String.class), any(String.class), any(Duration.class));
    }

    @Test
    void logoutReplayRecordIsClientScopedAndExpiresWithLogoutToken() {
        String token = BackChannelLogoutTest.jwt(BackChannelLogoutTest.claims());
        store.storeLogoutToken(token);
        var ttl = org.mockito.ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq("oidc:ACCOUNT_MANAGEMENT_CLIENT:logout_token:jti"), eq(token), ttl.capture());
        assertThat(ttl.getValue()).isBetween(Duration.ofSeconds(295), Duration.ofSeconds(300));
        when(values.get("oidc:ACCOUNT_MANAGEMENT_CLIENT:logout_token:jti")).thenReturn(token);
        assertThat(store.logoutTokenExists("jti")).isTrue();
        assertThat(store.logoutTokenExists("unknown")).isFalse();
    }

    @Test
    void backChannelCleanupDeletesOnlyMappedSessionTokensAndMapping() {
        when(values.get("oidc:ACCOUNT_MANAGEMENT_CLIENT:sid:sid")).thenReturn("session");
        store.clearTokensBySid("sid");
        verify(redis).delete("session:access_token");
        verify(redis).delete("session:id_token");
        verify(redis).delete("session:refresh_token");
        verify(redis).delete("oidc:ACCOUNT_MANAGEMENT_CLIENT:sid:sid");
        verify(redis, never()).delete("oidc:sid:sid");
        verify(redis, never()).delete("session:" + TokenType.OAUTH_STATE.key());
    }
}
