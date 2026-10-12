package com.roots.authserver.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.roots.authserver.service.LogoutTokenService;

import jakarta.servlet.http.HttpSession;

@ExtendWith(MockitoExtension.class)
class LogoutTokenServiceTest {

    @Mock private JwtEncoder jwtEncoder;
    @Mock private Authentication authentication;
    @Mock private HttpSession session;

    @Test
    void generateLogoutToken_expiresFiveMinutesAfterIssuance() throws Exception {
        when(authentication.getName()).thenReturn("user@example.com");
        when(session.getId()).thenReturn("session-123");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(
                Jwt.withTokenValue("signed-logout-token")
                        .header("alg", "RS256")
                        .claim("sub", "user@example.com")
                        .build());
        Instant before = Instant.now();

        String token = new LogoutTokenService(jwtEncoder).generateLogoutToken(
                "WEB_CLIENT", "http://localhost:9000", authentication, session);

        Instant after = Instant.now();
        ArgumentCaptor<JwtEncoderParameters> parameters = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(parameters.capture());
        JwtClaimsSet claims = parameters.getValue().getClaims();
        assertThat(token).isEqualTo("signed-logout-token");
        assertThat(claims.getIssuedAt()).isBetween(before, after);
        assertThat(claims.getExpiresAt()).isEqualTo(claims.getIssuedAt().plus(Duration.ofMinutes(5)));
        assertThat(claims.getIssuer().toString()).isEqualTo("http://localhost:9000");
        assertThat(claims.getSubject()).isEqualTo("user@example.com");
        assertThat(claims.getAudience()).isEqualTo(List.of("WEB_CLIENT"));
        assertThat(claims.getClaimAsString("sid")).isNotBlank();
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.<Map<String, Object>>getClaim("events"))
                .containsEntry("http://schemas.openid.net/event/backchannel-logout", Map.of());
    }
}
