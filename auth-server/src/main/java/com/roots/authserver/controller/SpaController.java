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

import com.roots.authserver.principal.CreateAccountPendingAuthenticationToken;
import com.roots.authserver.principal.GuestAuthenticationToken;
import com.roots.authserver.principal.MfaAuthenticationToken;
import com.roots.authserver.principal.MfaPendingAuthenticationToken;
import com.roots.authserver.service.InMemoryOneTimePinService;
import com.roots.authserver.service.UserCredentialService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Forwards all non-file, non-API requests to index.html so that
 * Nuxt's client-side router handles them correctly.
 */
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

    @GetMapping("/")
    public String forwardRoot() {
        return "forward:/index.html";
    }

    @GetMapping("/login")
    public String forwardLogin() {
        return "forward:/login/index.html";
    }

    @GetMapping("/ott/login")
    public String forwardOttSent() {
        return "forward:/ott/login/index.html";
    }

    @GetMapping("/magic-link/login")
    public String forwardMagicLinkSent(
            @RequestParam(required = false) String magicLinkToken,
            HttpServletRequest request) {
        // The browser reliably sends the token to the server here, but the Nuxt
        // SPA strips the query string during client-side hydration, so the page
        // can't read it. Capture it server-side and stash it in the session; the
        // POST below reads it from there.
        if (magicLinkToken != null) {
            request.getSession().setAttribute(MAGIC_LINK_TOKEN_SESSION_ATTR, magicLinkToken);
        }
        return "forward:/magic-link/login/index.html";
    }

    @GetMapping("/signup/success")
    public String forwardSignupSuccess() {
        return "forward:/signup/success/index.html";
    }

    @PostMapping("/ott/login")
    public String verifyOtt(
            @RequestParam String ott,
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

    @PostMapping("/magic-link/login")
    public String verifyMagicLink(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof CreateAccountPendingAuthenticationToken pending)) {
            return "redirect:/login";
        }

        // The token was captured from the magic link's query string on GET and
        // stored in the session (see forwardMagicLinkSent); the button-triggered
        // POST carries no token field.
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
