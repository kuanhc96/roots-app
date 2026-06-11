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

import com.roots.authserver.config.OpenApiConfig;
import com.roots.authserver.principal.MfaPendingAuthenticationToken;
import com.roots.authserver.service.EmailService;

import com.roots.authserver.service.InMemoryOneTimePinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "MFA & one-time tokens", description = "MFA one-time-token generation and integration-test-only token helpers")
public class MfaController {
    private final InMemoryOneTimePinService inMemoryOneTimePinService;
    private final JdbcOneTimeTokenService jdbcOneTimeTokenService;
    private final EmailService emailService;

    @Operation(
            summary = "Generate and email an MFA one-time token",
            description = "Generates an MFA one-time token for the user held in the current "
                    + "MfaPendingAuthenticationToken session, logs it to stdout, and emails it via EmailService. "
                    + "Called by the Nuxt /ott/login page on mount.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token generated and emailed"),
            @ApiResponse(responseCode = "403", description = "No MfaPendingAuthenticationToken in the session")
    })
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

    @Operation(
            summary = "[Integration test only] Generate an MFA one-time token and return its value",
            description = "Integration-test-only variant of POST /ott/generate. Behaves identically but "
                    + "returns the one-time token value in the response body so tests can complete the MFA "
                    + "flow without reading the email. Still reads the pending user from the session. "
                    + "Requires an INTEGRATION_TEST_CLIENT access token with the INTEGRATION_TEST_CLIENT_WRITE scope.",
            security = @SecurityRequirement(name = OpenApiConfig.INTEGRATION_TEST_BEARER))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token generated; value returned in the body"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Token lacks INTEGRATION_TEST_CLIENT_WRITE, or no pending session")
    })
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

    @Operation(
            summary = "[Integration test only] Mint an account-creation magic-link token and return its value",
            description = "Integration-test-only helper that mints the account-creation magic-link token via "
                    + "JdbcOneTimeTokenService for the given email and returns its value, so tests can verify "
                    + "email without reading the inbox. Stateless: the target email is passed explicitly rather "
                    + "than read from the session, since a client_credentials caller has no browser session. "
                    + "Requires an INTEGRATION_TEST_CLIENT access token with the INTEGRATION_TEST_CLIENT_WRITE scope.",
            security = @SecurityRequirement(name = OpenApiConfig.INTEGRATION_TEST_BEARER))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token minted; value returned in the body"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Token lacks INTEGRATION_TEST_CLIENT_WRITE")
    })
    @PostMapping("/magic-link/generate/test")
    @PreAuthorize("hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')")
    public ResponseEntity<String> generateMagicLinkTokenForTest(
            @Parameter(description = "Email of the account to mint the magic-link token for", required = true)
            @RequestParam String email) {
        GenerateOneTimeTokenRequest generateOneTimeTokenRequest = new GenerateOneTimeTokenRequest(email);
        OneTimeToken oneTimeToken = jdbcOneTimeTokenService.generate(generateOneTimeTokenRequest);
        return ResponseEntity.ok(oneTimeToken.getTokenValue());
    }
}
