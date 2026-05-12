package com.roots.authserver.controller;

import java.security.Principal;

import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.InMemoryOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MfaController {
    private final InMemoryOneTimeTokenService oneTimeTokenService;

    @PostMapping("/ott/generate")
    public void generateOneTimeToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        GenerateOneTimeTokenRequest generateOneTimeTokenRequest = new GenerateOneTimeTokenRequest(authentication.getName());
        OneTimeToken oneTimeToken = oneTimeTokenService.generate(generateOneTimeTokenRequest);
        System.out.println(oneTimeToken.getTokenValue());
    }
}
