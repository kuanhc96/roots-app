package com.roots.bff_server.service;

import com.roots.bff_server.client.AuthServerTokenClient;
import com.roots.bff_server.dto.response.TokenResponse;
import com.roots.bff_server.dto.response.LoginStatusResponse;
import com.roots.bff_server.enums.TokenType;
import com.roots.bff_server.util.JwtPayload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

/**
 * Answers "does this session have a valid login?" from the Redis token store.
 *
 * <p>An id_token under the session is the login marker — its presence alone is proof
 * (Redis expires the key at the token's exp, so no expiry check is needed). With no
 * id_token but a refresh token, the login is revived via a refresh exchange with
 * auth-server: the fresh tokens are stored (JWTs with TTL = their exp, the rotated
 * refresh token with the configured TTL) and the claims returned. With neither, or
 * when the exchange fails, there is no login — a failed exchange also deletes the
 * stored refresh token, since rotation means a rejected token can never succeed later.
 */
@Service
@RequiredArgsConstructor
public class AuthStatusService {

    private static final Logger log = LoggerFactory.getLogger(AuthStatusService.class);

    private final TokenStoreService tokenStore;
    private final AuthServerTokenClient authServerTokenClient;

    // Field injection (not a constructor arg) so @RequiredArgsConstructor keeps wiring
    // the final dependencies — a generated constructor would drop the @Value annotation
    // and fail to bind (same pattern as auth-server's EmailService).
    // TODO: get this value from the DB
    @Value("${token-store.refresh-token-ttl-seconds}")
    private long refreshTokenTtlSeconds;

    public LoginStatusResponse getLoginStatus(String sessionId) {
        Optional<String> idToken = tokenStore.find(sessionId, TokenType.ID_TOKEN);
        if (idToken.isPresent()) {
            try {
                return toLoggedInResponse(JwtPayload.parse(idToken.get()));
            } catch (IllegalArgumentException e) {
                log.warn("Discarding undecodable stored id_token for session {}", sessionId);
                tokenStore.delete(sessionId, TokenType.ID_TOKEN);
            }
        }

        Optional<String> refreshToken = tokenStore.find(sessionId, TokenType.REFRESH_TOKEN);
        if (refreshToken.isEmpty()) {
            return LoginStatusResponse.notLoggedIn();
        }

        Optional<TokenResponse> tokens = authServerTokenClient.refreshTokens(refreshToken.get())
                .filter(response -> response.idToken() != null);
        if (tokens.isEmpty()) {
            tokenStore.delete(sessionId, TokenType.REFRESH_TOKEN);
            return LoginStatusResponse.notLoggedIn();
        }

        storeTokens(sessionId, tokens.get());
        return toLoggedInResponse(JwtPayload.parse(tokens.get().idToken()));
    }

    private void storeTokens(String sessionId, TokenResponse tokens) {
        storeJwt(sessionId, TokenType.ACCESS_TOKEN, tokens.accessToken());
        storeJwt(sessionId, TokenType.ID_TOKEN, tokens.idToken());

        // Rotation (reuse-refresh-tokens=false) invalidated the refresh token that was
        // just used, so the stored one is replaced by the new one — or dropped in the
        // unexpected case the response carries none.
        if (tokens.refreshToken() != null) {
            tokenStore.store(sessionId, TokenType.REFRESH_TOKEN, tokens.refreshToken(),
                    Duration.ofSeconds(refreshTokenTtlSeconds));
        } else {
            tokenStore.delete(sessionId, TokenType.REFRESH_TOKEN);
        }
    }

    /** Stores a JWT with TTL = its own exp, so Redis drops it the moment it expires. */
    private void storeJwt(String sessionId, TokenType type, String jwt) {
        if (jwt == null) {
            return;
        }
        Duration timeToLive = Duration.between(Instant.now(), JwtPayload.parse(jwt).expiresAt());
        tokenStore.store(sessionId, type, jwt, timeToLive);
    }

    private static LoginStatusResponse toLoggedInResponse(JwtPayload idTokenPayload) {
        return LoginStatusResponse.loggedIn(
                idTokenPayload.getString("email"),
                idTokenPayload.getString("userGUID"),
                idTokenPayload.getStringList("roles"));
    }
}
