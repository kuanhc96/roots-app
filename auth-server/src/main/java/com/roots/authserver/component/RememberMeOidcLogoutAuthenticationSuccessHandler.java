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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.util.Assert;

/**
 * An {@link AuthenticationSuccessHandler} that clears the {@code remember-me} cookie
 * before delegating to an {@link OidcLogoutAuthenticationSuccessHandler}.
 */
public final class RememberMeOidcLogoutAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String DEFAULT_REMEMBER_ME_COOKIE_NAME = "remember-me";

    private final OidcLogoutAuthenticationSuccessHandler delegate;

    private String rememberMeCookieName = DEFAULT_REMEMBER_ME_COOKIE_NAME;

    public RememberMeOidcLogoutAuthenticationSuccessHandler(OidcLogoutAuthenticationSuccessHandler delegate) {
        Assert.notNull(delegate, "delegate cannot be null");
        this.delegate = delegate;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

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

    public void setRememberMeCookieName(String rememberMeCookieName) {
        Assert.hasText(rememberMeCookieName, "rememberMeCookieName cannot be empty");
        this.rememberMeCookieName = rememberMeCookieName;
    }

}
