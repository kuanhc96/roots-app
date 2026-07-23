package com.roots.bff_server.service;

import com.roots.bff_server.client.AuthServerTokenClient;
import com.roots.bff_server.dto.response.TokenResponse;
import com.roots.bff_server.dto.response.LoginStatusResponse;
import com.roots.bff_server.enums.TokenType;
import com.roots.bff_server.util.JwtPayload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

        tokenStore.storeTokenResponse(sessionId, tokens.get());
        return toLoggedInResponse(JwtPayload.parse(tokens.get().idToken()));
    }

    private static LoginStatusResponse toLoggedInResponse(JwtPayload idTokenPayload) {
        return LoginStatusResponse.loggedIn(
                idTokenPayload.getString("email"),
                idTokenPayload.getString("userGUID"),
                idTokenPayload.getStringList("roles"));
    }
}
