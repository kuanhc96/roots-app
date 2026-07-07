package com.roots.authserver.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.JdbcOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.roots.authserver.service.InMemoryOneTimePinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MfaController {
    private final InMemoryOneTimePinService inMemoryOneTimePinService;
    private final JdbcOneTimeTokenService jdbcOneTimeTokenService;

    @Operation(
            summary = "Generate the MFA one-time token for a given email (integration tests only)",
            description = "Integration-test-only variant of /ott/generate. Mints the MFA OTT for the "
                    + "given email and returns its value in the response body so tests can complete "
                    + "the MFA flow without reading the email. Stateless like /magic-link/generate/test: "
                    + "the email is passed explicitly, because the bearer-authenticated caller's context "
                    + "replaces the session's pending token for this request, so the pending user cannot "
                    + "be read from the SecurityContextHolder here. Verification is still session-bound — "
                    + "POST /ott/login checks the consumed token's username against the session's pending "
                    + "user. Guarded by the INTEGRATION_TEST_CLIENT_WRITE scope."
    )
    @PostMapping("/ott/generate/test")
    @PreAuthorize("hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')")
    public ResponseEntity<String> generateOneTimeTokenForTest(
            @Parameter(description = "Email of the account to mint the MFA one-time token for")
            @RequestParam String email) {
        GenerateOneTimeTokenRequest generateOneTimeTokenRequest = new GenerateOneTimeTokenRequest(email);
        OneTimeToken oneTimeToken = inMemoryOneTimePinService.generate(generateOneTimeTokenRequest);
        return ResponseEntity.ok(oneTimeToken.getTokenValue());
    }

    @Operation(
            summary = "Generate the account-creation magic-link token for a given email (integration tests only)",
            description = "Integration-test-only helper that mints the account-creation magic-link token "
                    + "via JdbcOneTimeTokenService for the given email and returns its value, so tests "
                    + "can verify email without reading the inbox. Called by the integration-test client "
                    + "over client_credentials, so the target email is passed explicitly rather than read "
                    + "from the session. Guarded by the INTEGRATION_TEST_CLIENT_WRITE scope."
    )
    @PostMapping("/magic-link/generate/test")
    @PreAuthorize("hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')")
    public ResponseEntity<String> generateMagicLinkTokenForTest(
            @Parameter(description = "Email of the account to mint the magic-link token for")
            @RequestParam String email) {
        GenerateOneTimeTokenRequest generateOneTimeTokenRequest = new GenerateOneTimeTokenRequest(email);
        OneTimeToken oneTimeToken = jdbcOneTimeTokenService.generate(generateOneTimeTokenRequest);
        return ResponseEntity.ok(oneTimeToken.getTokenValue());
    }
}
