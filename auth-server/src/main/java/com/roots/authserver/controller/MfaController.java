package com.roots.authserver.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.JdbcOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.roots.authserver.principal.MfaPendingAuthenticationToken;
import com.roots.authserver.service.EmailService;

import com.roots.authserver.service.InMemoryOneTimePinService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MfaController {
    private final InMemoryOneTimePinService inMemoryOneTimePinService;
    private final JdbcOneTimeTokenService jdbcOneTimeTokenService;
    private final EmailService emailService;

    @PostMapping("/ott/generate")
    public ResponseEntity<Void> generateOneTimeToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof MfaPendingAuthenticationToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        GenerateOneTimeTokenRequest generateOneTimeTokenRequest = new GenerateOneTimeTokenRequest(authentication.getName());
        OneTimeToken oneTimeToken = inMemoryOneTimePinService.generate(generateOneTimeTokenRequest);
        emailService.sendOTTEmail(authentication.getName(), oneTimeToken.getTokenValue());
        return ResponseEntity.ok().build();
    }

    // Integration-test-only variant of /ott/generate. Returns the OTT value in the
    // response body so tests can complete the MFA flow without reading the email.
    // Guarded by the INTEGRATION_TEST_CLIENT_WRITE scope (client_credentials).
    @PostMapping("/ott/generate/test")
    @PreAuthorize("hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')")
    public ResponseEntity<String> generateOneTimeTokenForTest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof MfaPendingAuthenticationToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        GenerateOneTimeTokenRequest generateOneTimeTokenRequest = new GenerateOneTimeTokenRequest(authentication.getName());
        OneTimeToken oneTimeToken = inMemoryOneTimePinService.generate(generateOneTimeTokenRequest);
        emailService.sendOTTEmail(authentication.getName(), oneTimeToken.getTokenValue());
        return ResponseEntity.ok(oneTimeToken.getTokenValue());
    }

    // Integration-test-only helper that mints the account-creation magic-link token
    // via JdbcOneTimeTokenService for the given email and returns its value, so tests
    // can verify email without reading the inbox. Called by the integration-test
    // client over client_credentials, so the target email is passed explicitly rather
    // than read from the session. Guarded by the INTEGRATION_TEST_CLIENT_WRITE scope.
    @PostMapping("/magic-link/generate/test")
    @PreAuthorize("hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')")
    public ResponseEntity<String> generateMagicLinkTokenForTest(@RequestParam String email) {
        GenerateOneTimeTokenRequest generateOneTimeTokenRequest = new GenerateOneTimeTokenRequest(email);
        OneTimeToken oneTimeToken = jdbcOneTimeTokenService.generate(generateOneTimeTokenRequest);
        return ResponseEntity.ok(oneTimeToken.getTokenValue());
    }
}
