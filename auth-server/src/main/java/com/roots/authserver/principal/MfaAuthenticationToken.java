package com.roots.authserver.principal;

import java.time.Instant;
import java.util.Collection;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class MfaAuthenticationToken extends UsernamePasswordAuthenticationToken {
    public MfaAuthenticationToken(UserDetails principal, Collection<? extends GrantedAuthority> authorities) {
        super(principal, "", authorities.stream().map(a -> FactorGrantedAuthority.withAuthority(a.getAuthority()).issuedAt(Instant.now()).build()).toList());
    }
}
