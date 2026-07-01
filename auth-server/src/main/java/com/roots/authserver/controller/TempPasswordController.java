package com.roots.authserver.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roots.authserver.dto.request.TempPasswordRequest;
import com.roots.authserver.service.EmailService;
import com.roots.authserver.service.UserCredentialService;
import lombok.RequiredArgsConstructor;

/**
 * Forgot-password entry point. Always returns 200 regardless of whether the email
 * matches an account, so the response never reveals which addresses are registered.
 * When a match exists, a temporary password is generated, persisted, and emailed.
 */
@RestController
@RequestMapping("/api/temp-password")
@RequiredArgsConstructor
public class TempPasswordController {

    private final UserCredentialService userCredentialService;
    private final EmailService emailService;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public void requestTempPassword(@RequestBody TempPasswordRequest request) {
        String tempPassword = userCredentialService.requestTempPassword(request.email());
        if (tempPassword != null) {
            emailService.sendTempPasswordEmail(request.email(), tempPassword);
        }
    }
}
