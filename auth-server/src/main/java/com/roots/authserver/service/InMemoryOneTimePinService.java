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
    private final Map<String, String> activePinByUsername = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastIssuedAtByUsername = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public OneTimeToken generate(GenerateOneTimeTokenRequest request) {
        String username = request.getUsername();
        String previousPin = activePinByUsername.remove(username);
        if (previousPin != null) {
            pins.remove(previousPin);
        }

        String pin = nextPin(previousPin);
        Instant now = Instant.now();
        OneTimeToken token = new DefaultOneTimeToken(pin, username, now.plus(Duration.ofMinutes(5)));
        pins.put(pin, token);
        activePinByUsername.put(username, pin);
        lastIssuedAtByUsername.put(username, now);
        return token;
    }

    @Override
    public OneTimeToken consume(OneTimeTokenAuthenticationToken authentication) {
        String pin = authentication.getTokenValue();
        OneTimeToken token = pins.remove(pin);
        if (token == null) {
            return null;
        }

        activePinByUsername.remove(token.getUsername(), pin);
        if (Instant.now().isAfter(token.getExpiresAt())) {
            return null;
        }
        return token;
    }

    public long getResendCooldownRemainingSeconds(String username, Duration cooldown) {
        Instant lastIssuedAt = lastIssuedAtByUsername.get(username);
        if (lastIssuedAt == null) {
            return 0;
        }

        long remainingMillis = Duration.between(Instant.now(), lastIssuedAt.plus(cooldown)).toMillis();
        if (remainingMillis <= 0) {
            return 0;
        }
        return (remainingMillis + 999) / 1000;
    }

    private String nextPin(String previousPin) {
        String pin;
        do {
            pin = String.format("%06d", random.nextInt(1_000_000));
        } while (pin.equals(previousPin) || pins.containsKey(pin));
        return pin;
    }
}
