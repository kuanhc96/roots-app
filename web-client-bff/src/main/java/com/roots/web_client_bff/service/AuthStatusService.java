package com.roots.web_client_bff.service;

import com.roots.web_client_bff.dto.response.TokenResponse;
import com.roots.web_client_bff.dto.response.LoginStatusResponse;
import com.roots.web_client_bff.enums.TokenType;
import com.roots.web_client_bff.util.JwtPayload;

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
 * id_token, the login is revived via {@link TokenRefreshService} (which stores the
 * fresh tokens, or deletes a rejected refresh token) and the claims returned. With no
 * refresh token, or when the exchange fails, there is no login.
 */
@Service
@RequiredArgsConstructor
public class AuthStatusService {

    private static final Logger log = LoggerFactory.getLogger(AuthStatusService.class);

    private final TokenStoreService tokenStore;
    private final TokenRefreshService tokenRefreshService;

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

        Optional<TokenResponse> tokens = tokenRefreshService.refresh(sessionId)
                .filter(response -> response.idToken() != null);
        if (tokens.isEmpty()) {
            return LoginStatusResponse.notLoggedIn();
        }

        return toLoggedInResponse(JwtPayload.parse(tokens.get().idToken()));
    }

    private static LoginStatusResponse toLoggedInResponse(JwtPayload idTokenPayload) {
        return LoginStatusResponse.loggedIn(
                idTokenPayload.getString("email"),
                idTokenPayload.getString("userGUID"),
                idTokenPayload.getStringList("roles"));
    }
}
