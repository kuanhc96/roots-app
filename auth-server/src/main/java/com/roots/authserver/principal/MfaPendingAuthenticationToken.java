package com.roots.authserver.principal;

import java.util.Collections;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;

public class MfaPendingAuthenticationToken extends AbstractAuthenticationToken {
    private final UserDetails principal;

    public MfaPendingAuthenticationToken(UserDetails principal) {
        super(Collections.emptyList());
        this.principal = principal;
        setAuthenticated(false);
    }

    @Override public Object getCredentials() {return "";}
    @Override public Object getPrincipal() {return principal;}
}
