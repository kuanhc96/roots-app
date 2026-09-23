package com.roots.authserver.component;

/*
 * Copyright 2004-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.util.Assert;

import com.roots.authserver.repository.CustomJdbcRegisteredClientRepository;
import com.roots.authserver.service.LogoutTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

/**
 * An {@link AuthenticationSuccessHandler} that sends back-channel logout notifications
 * to registered clients before clearing the remember-me cookie and delegating to
 * {@link OidcLogoutAuthenticationSuccessHandler} for the RP-Initiated Logout redirect.
 */
@Slf4j
public final class CustomOidcLogoutAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String DEFAULT_REMEMBER_ME_COOKIE_NAME = "remember-me";

    private final OidcLogoutAuthenticationSuccessHandler delegate;
    private final CustomJdbcRegisteredClientRepository registeredClientRepository;
    private final LogoutTokenService logoutTokenService;

    private String rememberMeCookieName = DEFAULT_REMEMBER_ME_COOKIE_NAME;

    @Value("${auth-server.external-location}")
    private String iss;

    public CustomOidcLogoutAuthenticationSuccessHandler(
            OidcLogoutAuthenticationSuccessHandler delegate,
            CustomJdbcRegisteredClientRepository registeredClientRepository,
            LogoutTokenService logoutTokenService) {
        Assert.notNull(delegate, "delegate cannot be null");
        Assert.notNull(registeredClientRepository, "registeredClientRepository cannot be null");
        Assert.notNull(logoutTokenService, "logoutTokenService cannot be null");
        this.delegate = delegate;
        this.registeredClientRepository = registeredClientRepository;
        this.logoutTokenService = logoutTokenService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        HttpSession session = request.getSession(false);
        if (session != null) {
            List<RegisteredClient> registeredClients = registeredClientRepository.findAll();
            // Send back-channel logout notifications asynchronously so slow RPs don't block the redirect
            logoutTokenService.sendBackChannelLogoutAsync(registeredClients, null, iss, authentication,
                    session);
        } else {
            log.warn("Session not found during logout; skipping back-channel logout notifications");
        }

        clearRememberMeCookie(request, response);
        this.delegate.onAuthenticationSuccess(request, response, authentication);
    }

    private void clearRememberMeCookie(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie(this.rememberMeCookieName, "");
        cookie.setMaxAge(0);
        cookie.setPath(getCookiePath(request));
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        response.addCookie(cookie);
    }

    private String getCookiePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return contextPath.length() > 0 ? contextPath : "/";
    }

}
