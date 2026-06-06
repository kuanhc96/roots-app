package com.roots.authserver.component;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.JdbcOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.roots.authserver.principal.CreateAccountPendingAuthenticationToken;
import com.roots.authserver.principal.MfaPendingAuthenticationToken;
import com.roots.authserver.service.EmailService;
import com.roots.authserver.service.InMemoryOneTimePinService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MfaRedirectAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final InMemoryOneTimePinService inMemoryOneTimePinService;
    private final JdbcOneTimeTokenService jdbcOneTimeTokenService;
    private final EmailService emailService;

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication auth
    ) throws IOException, ServletException {
        if (auth instanceof MfaPendingAuthenticationToken) {
            GenerateOneTimeTokenRequest generateOneTimeTokenRequest = new GenerateOneTimeTokenRequest(auth.getName());
            OneTimeToken oneTimeToken = inMemoryOneTimePinService.generate(generateOneTimeTokenRequest);
            emailService.sendOTTEmail(auth.getName(), oneTimeToken.getTokenValue());
            response.sendRedirect("/ott/login");
        } else if (auth instanceof CreateAccountPendingAuthenticationToken) {
            GenerateOneTimeTokenRequest generateOneTimeTokenRequest = new GenerateOneTimeTokenRequest(auth.getName());
            OneTimeToken oneTimeToken = jdbcOneTimeTokenService.generate(generateOneTimeTokenRequest);
            String magicLink = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/magic-link/login")
                    .queryParam("magicLinkToken", oneTimeToken.getTokenValue())
                    .build()
                    .toUriString();
            System.out.println("MAGIC LINK: " + magicLink);
            emailService.sendMagicLinkEmail(auth.getName(), magicLink);
            response.sendRedirect("/signup/success");
        } else {
            super.onAuthenticationSuccess(request, response, auth);
        }
    }
}
