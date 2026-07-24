package com.roots.authserver.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriUtils;

import com.roots.authserver.service.GoogleAuthorizeService;

@ExtendWith(MockitoExtension.class)
class GoogleAuthorizeServiceTest {

    private static final String SESSION_ID = "session-123";
    private static final String REDIRECT_URI = "http://localhost:9000/login/google/callback";
    private static final String STATE_KEY = SESSION_ID + ":oauth_state";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private GoogleAuthorizeService googleAuthorizeService;

    @BeforeEach
    void setUp() {
        googleAuthorizeService = new GoogleAuthorizeService(redisTemplate);
        ReflectionTestUtils.setField(googleAuthorizeService, "googleClientId", "client-id");
    }

    @Test
    void buildAuthorizeRedirect_storesStateInRedisWithFiveMinuteTtl_andBuildsGoogleAuthorizeUrl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        URI redirect = googleAuthorizeService.buildAuthorizeRedirect(SESSION_ID, REDIRECT_URI);

        ArgumentCaptor<String> stateCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(STATE_KEY), stateCaptor.capture(), eq(Duration.ofMinutes(5)));
        String mintedState = stateCaptor.getValue();
        assertThat(mintedState).isNotBlank();

        assertThat(redirect.toString()).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        assertThat(redirect.toString()).contains("response_type=code");
        assertThat(redirect.toString()).contains("client_id=client-id");
        assertThat(redirect.toString()).contains(
                "redirect_uri=" + UriUtils.encodeQueryParam(REDIRECT_URI, StandardCharsets.UTF_8));
        assertThat(redirect.toString()).contains(
                "scope=" + UriUtils.encodeQueryParam("openid email profile", StandardCharsets.UTF_8));
        assertThat(redirect.toString()).contains("state=" + mintedState);
    }

    @Test
    void consumeAndValidateState_matchingState_returnsTrue_andConsumesTheKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(STATE_KEY)).thenReturn("expected-state");

        boolean valid = googleAuthorizeService.consumeAndValidateState(SESSION_ID, "expected-state");

        assertThat(valid).isTrue();
        verify(redisTemplate).delete(STATE_KEY);
    }

    @Test
    void consumeAndValidateState_mismatchedState_returnsFalse_butStillConsumesTheKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(STATE_KEY)).thenReturn("expected-state");

        boolean valid = googleAuthorizeService.consumeAndValidateState(SESSION_ID, "wrong-state");

        assertThat(valid).isFalse();
        verify(redisTemplate).delete(STATE_KEY);
    }

    @Test
    void consumeAndValidateState_noStoredState_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(STATE_KEY)).thenReturn(null);

        boolean valid = googleAuthorizeService.consumeAndValidateState(SESSION_ID, "some-state");

        assertThat(valid).isFalse();
        verify(redisTemplate).delete(STATE_KEY);
    }

    @Test
    void consumeAndValidateState_nullReturnedState_returnsFalse_withoutTouchingRedisLookup() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(STATE_KEY)).thenReturn("expected-state");

        boolean valid = googleAuthorizeService.consumeAndValidateState(SESSION_ID, null);

        assertThat(valid).isFalse();
        verify(redisTemplate).delete(STATE_KEY);
    }
}
