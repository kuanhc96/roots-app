package com.roots.account_management_client_bff.validator;

import com.roots.account_management_client_bff.enums.TokenType;
import com.roots.account_management_client_bff.service.TokenStoreService;
import com.roots.account_management_client_bff.util.JwtPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LogoutTokenValidator {

    private final TokenStoreService tokenStoreService;

    @Value("${auth-server.internal-location}")
    private String authServerInternalLocation;
    @Value("${account-management.client.id}")
    private String clientId;

    // Matches web-client-bff's claim-only validation; signatures are not verified.
    public void validate(String logoutToken) {
        JwtPayload payload = JwtPayload.parse(logoutToken);
        if (tokenStoreService.logoutTokenExists(payload.getJti())) {
            reject("potential logout token replay");
        }
        if (!"RS256".equalsIgnoreCase(payload.getAlg())) {
            reject("invalid alg");
        }
        if (!authServerInternalLocation.equalsIgnoreCase(payload.getIss())) {
            reject("invalid iss");
        }
        if (!clientId.equalsIgnoreCase(payload.getAud())) {
            reject("invalid aud");
        }
        if (payload.getIat().isAfter(Instant.now())) {
            reject("iat cannot be in the future");
        }
        if (payload.expiresAt().isBefore(Instant.now())) {
            reject("token is expired");
        }
        if (!StringUtils.hasText(payload.getSub())) {
            reject("missing sub");
        }
        if (!StringUtils.hasText(payload.getSid())) {
            reject("missing sid claim");
        }
        if (StringUtils.hasText(payload.getNonce())) {
            reject("cannot contain nonce");
        }
        Object eventsClaim = payload.claims().get("events");
        if (!(eventsClaim instanceof Map<?, ?> events)
                || !events.containsKey("http://schemas.openid.net/event/backchannel-logout")) {
            reject("invalid events claim");
        }

        Optional<String> sessionId = tokenStoreService.findSessionIdBySid(payload.getSid());
        if (sessionId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid logout_token: sid not found");
        }
        Optional<String> idToken = tokenStoreService.find(sessionId.get(), TokenType.ID_TOKEN);
        if (idToken.isPresent()) {
            JwtPayload stored = JwtPayload.parse(idToken.get());
            if (!stored.getIss().equalsIgnoreCase(payload.getIss())) {
                reject("invalid iss");
            }
            if (!stored.getSub().equalsIgnoreCase(payload.getSub())) {
                reject("invalid sub");
            }
            if (!stored.getSid().equalsIgnoreCase(payload.getSid())) {
                reject("invalid sid");
            }
        }
    }

    private static void reject(String reason) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: " + reason);
    }
}
