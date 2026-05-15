package com.roots.authserver.principal;

import java.util.Collections;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class GuestAuthenticationToken extends AbstractAuthenticationToken {

    public GuestAuthenticationToken() {
        super(Collections.emptyList());
        setAuthenticated(false);
    }

    @Override public Object getCredentials() { return ""; }
    @Override public Object getPrincipal() { return "guest"; }
}
