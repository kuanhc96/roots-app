package com.roots.authserver.service;

import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.JdbcOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import lombok.RequiredArgsConstructor;

/**
 * Issues the account-creation magic-link token and emails it. Shared by the signup
 * endpoint (which establishes the email-verification pending session directly) and
 * the login success handler (a manual login with an unverified email). Must run on
 * a request thread: the absolute link is built from the current request's context.
 */
@Service
@RequiredArgsConstructor
public class MagicLinkService {

    private final JdbcOneTimeTokenService jdbcOneTimeTokenService;
    private final EmailService emailService;

    public void issueAndEmail(String email) {
        OneTimeToken oneTimeToken = jdbcOneTimeTokenService.generate(new GenerateOneTimeTokenRequest(email));
        String magicLink = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/magic-link/login")
                .queryParam("magicLinkToken", oneTimeToken.getTokenValue())
                .build()
                .toUriString();
        emailService.sendMagicLinkEmail(email, magicLink);
    }
}
