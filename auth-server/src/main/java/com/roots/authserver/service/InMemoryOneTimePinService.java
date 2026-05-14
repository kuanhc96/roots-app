package com.roots.authserver.service;

import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOneTimePinService implements OneTimeTokenService {
    private final Map<String, OneTimeToken> pins = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public OneTimeToken generate(GenerateOneTimeTokenRequest request) {
        String pin = String.format("%06d", random.nextInt(1_000_000));
        OneTimeToken token = new DefaultOneTimeToken(pin, request.getUsername(), Instant.now().plus(Duration.ofMinutes(5)));
        pins.put(pin, token);
        return token;
    }

    @Override
    public OneTimeToken consume(OneTimeTokenAuthenticationToken authentication) {
        String pin = authentication.getTokenValue();
        OneTimeToken token = pins.remove(pin);
        if (token == null || Instant.now().isAfter(token.getExpiresAt())) {
            return null;
        }
        return token;
    }
}
