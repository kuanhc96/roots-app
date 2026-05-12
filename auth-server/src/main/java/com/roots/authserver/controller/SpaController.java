package com.roots.authserver.controller;

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

import com.roots.authserver.principal.MfaAuthenticationToken;
import com.roots.authserver.principal.MfaPendingAuthenticationToken;
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
    private final OneTimeTokenService oneTimeTokenService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

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

    @PostMapping("/ott/login")
    public String verifyOtt(@RequestParam String ott, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof MfaPendingAuthenticationToken pending)) {
            return "redirect:/login";
        }

        UserDetails user = (UserDetails) pending.getPrincipal();
        if (null == oneTimeTokenService.consume(new OneTimeTokenAuthenticationToken(ott))) {
            return "redirect:/ott/login?error=invalidToken";
        } else {
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
}
