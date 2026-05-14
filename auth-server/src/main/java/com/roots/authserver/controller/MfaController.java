package com.roots.authserver.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roots.authserver.principal.MfaPendingAuthenticationToken;
import com.roots.authserver.service.EmailService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MfaController {
    private final OneTimeTokenService oneTimeTokenService;
    private final EmailService emailService;

    @PostMapping("/ott/generate")
    public ResponseEntity<Void> generateOneTimeToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof MfaPendingAuthenticationToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        GenerateOneTimeTokenRequest generateOneTimeTokenRequest = new GenerateOneTimeTokenRequest(authentication.getName());
        OneTimeToken oneTimeToken = oneTimeTokenService.generate(generateOneTimeTokenRequest);
        emailService.sendOTTEmail(authentication.getName(), oneTimeToken.getTokenValue());
        return ResponseEntity.ok().build();
    }
}
