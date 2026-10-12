package com.roots.web_client_bff.validator;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.common.util.StringUtils;

import com.roots.web_client_bff.enums.TokenType;
import com.roots.web_client_bff.service.TokenStoreService;
import com.roots.web_client_bff.util.JwtPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class LogoutTokenValidator {
    @Value("${auth-server.internal-location}")
    private String authServerInternalLocation;

    @Value("${web.client.id}")
    private String webClientId;

    public final TokenStoreService tokenStoreService;

    public void validate(String logoutToken) {
        JwtPayload jwtPayload = JwtPayload.parse(logoutToken);
        if (tokenStoreService.logoutTokenExists(jwtPayload.getJti())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: potential logout token replay");
        }

        String alg = jwtPayload.getAlg();
        if (!"RS256".equalsIgnoreCase(alg)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: invalid alg");
        }

        String iss = jwtPayload.getIss();
        if (!authServerInternalLocation.equalsIgnoreCase(iss)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: invalid iss");
        }
        String aud = jwtPayload.getAud();
        if (!webClientId.equalsIgnoreCase(aud)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: invalid aud");
        }
        Instant iat = jwtPayload.getIat();
        if (iat.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: iat cannot be in the future");
        }
        Instant exp = jwtPayload.expiresAt();
        if (exp.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: token is expired");
        }
        String sub = jwtPayload.getSub();
        if (StringUtils.isBlank(sub)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: missing sub");
        }
        String sid = jwtPayload.getSid();
        if (StringUtils.isBlank(sid)) {
            log.warn("Rejecting back-channel logout: malformed token or invalid sid");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: missing sid claim");
        }
        String nonce = jwtPayload.getNonce();
        if (StringUtils.isNotBlank(nonce)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: cannot contain nonce");
        }

        Optional<String> optionalSession = tokenStoreService.findSessionIdBySid(sid);
        if (optionalSession.isPresent()) {
            Optional<String> optionalIdToken = tokenStoreService.find(optionalSession.get(), TokenType.ID_TOKEN);
            if (optionalIdToken.isPresent()) {
                JwtPayload idTokenPayload = JwtPayload.parse(optionalIdToken.get());
                if (!idTokenPayload.getIss().equalsIgnoreCase(jwtPayload.getIss())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: invalid iss");
                }
                if (!idTokenPayload.getSub().equalsIgnoreCase(jwtPayload.getSub())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: invalid sub");
                }
                if (!idTokenPayload.getSid().equalsIgnoreCase(jwtPayload.getSid())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid logout_token: invalid sid");
                }
            }
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid logout_token: sid not found");
        }
    }
}
