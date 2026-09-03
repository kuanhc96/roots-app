package com.roots.account_management_bff.service;

import com.roots.account_management_bff.client.AuthServerTokenClient;
import com.roots.account_management_bff.dto.response.LoginStatusResponse;
import com.roots.account_management_bff.dto.response.TokenResponse;
import com.roots.account_management_bff.enums.TokenType;
import com.roots.account_management_bff.util.JwtPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

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
