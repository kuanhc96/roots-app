package com.roots.authserver.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.InMemoryOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roots.authserver.principal.MfaPendingAuthenticationToken;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MfaController {
    private final InMemoryOneTimeTokenService oneTimeTokenService;

    @PostMapping("/ott/generate")
    public ResponseEntity<Void> generateOneTimeToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof MfaPendingAuthenticationToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        GenerateOneTimeTokenRequest generateOneTimeTokenRequest = new GenerateOneTimeTokenRequest(authentication.getName());
        OneTimeToken oneTimeToken = oneTimeTokenService.generate(generateOneTimeTokenRequest);
        System.out.println("OTT: " + oneTimeToken.getTokenValue());
        return ResponseEntity.ok().build();
    }
}
