package com.roots.authserver.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ott.JdbcOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.roots.authserver.exception.InvalidRequestException;
import com.roots.authserver.principal.CreateAccountPendingAuthenticationToken;
import com.roots.authserver.principal.GuestAuthenticationToken;
import com.roots.authserver.principal.MfaAuthenticationToken;
import com.roots.authserver.principal.MfaPendingAuthenticationToken;
import com.roots.authserver.principal.PasswordChangePendingAuthenticationToken;
import com.roots.authserver.service.InMemoryOneTimePinService;
import com.roots.authserver.service.UserCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Tag(
        name = "SPA",
        description = "Forwards all non-file, non-API requests to index.html so that Nuxt's "
                + "client-side router handles them correctly, and hosts the browser-facing "
                + "form-post endpoints of the login flows (OTT, magic link, password reset, guest)."
)
@Controller
@RequiredArgsConstructor
public class SpaController {
    private final InMemoryOneTimePinService inMemoryOneTimePinService;
    private final JdbcOneTimeTokenService jdbcOneTimeTokenService;
    private final UserCredentialService userCredentialService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    private static final String MAGIC_LINK_TOKEN_SESSION_ATTR = "magicLinkToken";

    @Value("${web-client.location:http://localhost:3000}")
    private String webClientLocation;

    @Operation(
            summary = "Root page",
            description = "Forwards to the Nuxt index page.",
            responses = @ApiResponse(responseCode = "200", description = "The Nuxt index page",
                    content = @Content(mediaType = "text/html"))
    )
    @GetMapping("/")
    public String forwardRoot() {
        return "forward:/index.html";
    }

    @Operation(
            summary = "Login page",
            description = "Forwards to the Nuxt login page. Only GET reaches this controller — "
                    + "POST /login is intercepted by Spring Security's form-login filter before MVC.",
            responses = @ApiResponse(responseCode = "200", description = "The Nuxt login page",
                    content = @Content(mediaType = "text/html"))
    )
    @GetMapping("/login")
    public String forwardLogin() {
        return "forward:/login/index.html";
    }

    @Operation(
            summary = "OTT (MFA) login page",
            description = "Forwards to the Nuxt OTT login page, which calls POST /ott/generate on "
                    + "mount to trigger delivery of the one-time token.",
            responses = @ApiResponse(responseCode = "200", description = "The Nuxt OTT login page",
                    content = @Content(mediaType = "text/html"))
    )
    @GetMapping("/ott/login")
    public String forwardOttSent() {
        return "forward:/ott/login/index.html";
    }

    @Operation(
            summary = "Magic-link landing page",
            description = "Forwards to the Nuxt magic-link page. The browser reliably sends the "
                    + "magicLinkToken query parameter to the server here, but the Nuxt SPA strips the "
                    + "query string during client-side hydration, so the page can't read it — the token "
                    + "is captured server-side into the HTTP session, from which POST /magic-link/login "
                    + "later reads it.",
            responses = @ApiResponse(responseCode = "200", description = "The Nuxt magic-link login page",
                    content = @Content(mediaType = "text/html"))
    )
    @GetMapping("/magic-link/login")
    public String forwardMagicLinkSent(
            @Parameter(description = "Magic-link token from the emailed link; stashed in the session for the verifying POST")
            @RequestParam(required = false) String magicLinkToken,
            HttpServletRequest request) {
        if (magicLinkToken != null) {
            request.getSession().setAttribute(MAGIC_LINK_TOKEN_SESSION_ATTR, magicLinkToken);
        }
        return "forward:/magic-link/login/index.html";
    }

    @Operation(
            summary = "Signup success page",
            description = "Forwards to the Nuxt page shown after signup, telling the user to check "
                    + "their email for the magic link.",
            responses = @ApiResponse(responseCode = "200", description = "The Nuxt signup-success page",
                    content = @Content(mediaType = "text/html"))
    )
    @GetMapping("/signup/success")
    public String forwardSignupSuccess() {
        return "forward:/signup/success/index.html";
    }

    @Operation(
            summary = "Reset-password page",
            description = "Forwards to the Nuxt reset-password page; temp-password logins are "
                    + "redirected here to set a new password.",
            responses = @ApiResponse(responseCode = "200", description = "The Nuxt reset-password page",
                    content = @Content(mediaType = "text/html"))
    )
    @GetMapping("/reset-password")
    public String forwardResetPassword() {
        return "forward:/reset-password/index.html";
    }

    @Operation(
            summary = "Verify the MFA one-time token",
            description = "Verifies the submitted one-time token against the session's MFA-pending "
                    + "user (an MfaPendingAuthenticationToken must be in the session, else redirect "
                    + "to /login). If rememberBrowser=true, disables MFA for the user. On success, "
                    + "upgrades the session to a fully authenticated MfaAuthenticationToken and "
                    + "redirects to the saved OAuth2 authorization request; an invalid token redirects "
                    + "back to /ott/login with an error.",
            responses = @ApiResponse(responseCode = "302",
                    description = "Redirect to the saved OAuth2 request on success, or back to "
                            + "/ott/login or /login with an error",
                    content = @Content)
    )
    @PostMapping("/ott/login")
    public String verifyOtt(
            @Parameter(description = "The one-time token the user received")
            @RequestParam String ott,
            @Parameter(description = "When true, disables MFA for the user (\"Remember this browser?\")")
            @RequestParam(required = false, defaultValue = "false") boolean rememberBrowser,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof MfaPendingAuthenticationToken pending)) {
            return "redirect:/login";
        }

        UserDetails user = (UserDetails) pending.getPrincipal();
        var consumedToken = inMemoryOneTimePinService.consume(new OneTimeTokenAuthenticationToken(ott));
        if (consumedToken == null || !consumedToken.getUsername().equals(user.getUsername())) {
            return "redirect:/ott/login?error=invalidToken";
        } else {
            if (rememberBrowser) {
                userCredentialService.disableMfa(user.getUsername());
            }

            MfaAuthenticationToken full = new MfaAuthenticationToken(user, user.getAuthorities());
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(full);
            SecurityContextHolder.setContext(securityContext);
            securityContextRepository.saveContext(securityContext, request, response);

            SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
            if (savedRequest != null) {
                return "redirect:" + savedRequest.getRedirectUrl();
            } else {
                return "redirect:/ott/login?error=oauthRedirectFailed";
            }
        }
    }

    @Operation(
            summary = "Verify the account-creation magic link",
            description = "Completes email verification for a new account. Requires a "
                    + "CreateAccountPendingAuthenticationToken in the session (else redirect to "
                    + "/login). The magic-link token was captured from the link's query string by "
                    + "GET /magic-link/login and stored in the session — this button-triggered POST "
                    + "carries no token field. Consumes the token, verifies it belongs to the pending "
                    + "account, marks the email verified, upgrades the session, and redirects to the "
                    + "saved OAuth2 request; a missing or invalid token redirects back with "
                    + "error=invalidToken.",
            responses = @ApiResponse(responseCode = "302",
                    description = "Redirect to the saved OAuth2 request (or the web-client base URL "
                            + "when none exists), or back to /magic-link/login or /login with an error",
                    content = @Content)
    )
    @PostMapping("/magic-link/login")
    public String verifyMagicLink(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof CreateAccountPendingAuthenticationToken pending)) {
            return "redirect:/login";
        }

        String magicLinkToken = (String) request.getSession().getAttribute(MAGIC_LINK_TOKEN_SESSION_ATTR);
        if (magicLinkToken == null) {
            return "redirect:/magic-link/login?error=invalidToken";
        }

        UserDetails user = (UserDetails) pending.getPrincipal();
        var consumedToken = jdbcOneTimeTokenService.consume(new OneTimeTokenAuthenticationToken(magicLinkToken));
        if (consumedToken == null || !consumedToken.getUsername().equals(user.getUsername())) {
            return "redirect:/magic-link/login?error=invalidToken";
        } else {
            request.getSession().removeAttribute(MAGIC_LINK_TOKEN_SESSION_ATTR);
            userCredentialService.verifyEmail(user.getUsername());

            MfaAuthenticationToken full = new MfaAuthenticationToken(user, user.getAuthorities());
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(full);
            SecurityContextHolder.setContext(securityContext);
            securityContextRepository.saveContext(securityContext, request, response);

            SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
            if (savedRequest != null) {
                return "redirect:" + savedRequest.getRedirectUrl();
            } else {
                // No in-progress OAuth2 flow (direct /signup visit). Hand off to web-client,
                // which initiates /oauth2/authorize with its own state. The session is now
                // authenticated, so a code is issued and the callback succeeds.
                return "redirect:" + webClientLocation;
            }
        }
    }

    @Operation(
            summary = "Complete the forgot-password reset",
            description = "Sets the new password for a temp-password login. Requires a "
                    + "PasswordChangePendingAuthenticationToken in the session (else redirect to "
                    + "/login). Validates the new password against the shared complexity policy "
                    + "(failure redirects back with error=invalidPassword), stores it, clears the "
                    + "password-change flag, marks the email verified, upgrades the session, and "
                    + "redirects to the saved OAuth2 request.",
            responses = @ApiResponse(responseCode = "302",
                    description = "Redirect to the saved OAuth2 request (or the web-client base URL "
                            + "when none exists), or back to /reset-password or /login with an error",
                    content = @Content)
    )
    @PostMapping("/reset-password")
    public String resetPassword(
            @Parameter(description = "The new password; must satisfy the shared complexity policy")
            @RequestParam String newPassword,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof PasswordChangePendingAuthenticationToken pending)) {
            return "redirect:/login";
        }

        UserDetails user = (UserDetails) pending.getPrincipal();
        try {
            userCredentialService.completePasswordReset(user.getUsername(), newPassword);
        } catch (InvalidRequestException e) {
            return "redirect:/reset-password?error=invalidPassword";
        }

        MfaAuthenticationToken full = new MfaAuthenticationToken(user, user.getAuthorities());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(full);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
        if (savedRequest != null) {
            return "redirect:" + savedRequest.getRedirectUrl();
        } else {
            return "redirect:" + webClientLocation;
        }
    }

    @Operation(
            summary = "Log in as guest",
            description = "Authenticates the session as a synthetic guest user — no credentials or "
                    + "MFA; username \"guest\" with authority GUEST — and redirects to the saved "
                    + "OAuth2 authorization request.",
            responses = @ApiResponse(responseCode = "302",
                    description = "Redirect to the saved OAuth2 request on success, or back to "
                            + "/login with an error when none exists",
                    content = @Content)
    )
    @PostMapping("/login/guest")
    public String loginAsGuest(HttpServletRequest request, HttpServletResponse response) {
        Authentication guestAuth = authenticationManager.authenticate(new GuestAuthenticationToken());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(guestAuth);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
        if (savedRequest != null) {
            return "redirect:" + savedRequest.getRedirectUrl();
        } else {
            return "redirect:/login?error=oauthRedirectFailed";
        }
    }
}
