package com.roots.authserver.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roots.authserver.dto.request.TempPasswordRequest;
import com.roots.authserver.service.EmailService;
import com.roots.authserver.service.UserCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/temp-password")
@RequiredArgsConstructor
public class TempPasswordController {

    private final UserCredentialService userCredentialService;
    private final EmailService emailService;

    @Operation(
            summary = "Request a temporary password (forgot-password)",
            description = "Forgot-password entry point. Always returns 200 regardless of whether the "
                    + "email matches an account, so the response never reveals which addresses are "
                    + "registered. When a match exists, a temporary password is generated, persisted "
                    + "(overwriting the stored password), and emailed."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public void requestTempPassword(@RequestBody TempPasswordRequest request) {
        String tempPassword = userCredentialService.requestTempPassword(request.email());
        if (tempPassword != null) {
            emailService.sendTempPasswordEmail(request.email(), tempPassword);
        }
    }

    @Operation(
            summary = "Request a temporary password and return it (integration tests only)",
            description = "Integration-test-only variant of /api/temp-password: returns the plaintext "
                    + "temporary password in the response body instead of emailing it. Same side "
                    + "effects underneath — the stored password is overwritten with the temp password's "
                    + "hash and is_password_change_required is set to true. Guarded by the "
                    + "INTEGRATION_TEST_CLIENT_WRITE scope."
    )
    @PostMapping("/test")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')")
    public ResponseEntity<String> requestTempPasswordForTest(@RequestBody TempPasswordRequest request) {
        String tempPassword = userCredentialService.requestTempPassword(request.email());
        return ResponseEntity.ok().body(tempPassword);
    }

}
