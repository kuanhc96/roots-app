package com.roots.authserver.component;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.roots.authserver.principal.CreateAccountPendingAuthenticationToken;
import com.roots.authserver.principal.MfaPendingAuthenticationToken;
import com.roots.authserver.principal.PasswordChangePendingAuthenticationToken;
import com.roots.authserver.service.EmailService;
import com.roots.authserver.service.InMemoryOneTimePinService;
import com.roots.authserver.service.MagicLinkService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MfaRedirectAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final InMemoryOneTimePinService inMemoryOneTimePinService;
    private final EmailService emailService;
    private final MagicLinkService magicLinkService;

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication auth
    ) throws IOException, ServletException {
        if (auth instanceof PasswordChangePendingAuthenticationToken) {
            // Temp password accepted; force the user to set a new one. The temporary
            // password was already emailed during the forgot-password request, so nothing
            // is generated here — just send them to the reset form.
            response.sendRedirect("/reset-password");
        } else if (auth instanceof MfaPendingAuthenticationToken) {
            GenerateOneTimeTokenRequest generateOneTimeTokenRequest = new GenerateOneTimeTokenRequest(auth.getName());
            OneTimeToken oneTimeToken = inMemoryOneTimePinService.generate(generateOneTimeTokenRequest);
            emailService.sendOTTEmail(auth.getName(), oneTimeToken.getTokenValue());
            response.sendRedirect("/ott/login");
        } else if (auth instanceof CreateAccountPendingAuthenticationToken) {
            magicLinkService.issueAndEmail(auth.getName());
            response.sendRedirect("/signup/success");
        } else {
            super.onAuthenticationSuccess(request, response, auth);
        }
    }
}
