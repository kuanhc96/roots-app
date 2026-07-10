package com.roots.authserver.principal;

import java.time.Instant;
import java.util.Collection;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Fully authenticated session established by a verified social-provider id_token
 * (currently Google). Issued directly — the provider is trusted as both factors
 * (Google enforces its own 2FA), so {@code is_mfa_enabled} is deliberately ignored
 * on this path and no OTT step runs. Authorities are wrapped in
 * {@link FactorGrantedAuthority}, like {@link MfaAuthenticationToken}, as required
 * for OIDC logout in Spring Security 7.
 */
public class SocialLoginAuthenticationToken extends UsernamePasswordAuthenticationToken {
    public SocialLoginAuthenticationToken(UserDetails principal, Collection<? extends GrantedAuthority> authorities) {
        super(principal, "", authorities.stream().map(a -> FactorGrantedAuthority.withAuthority(a.getAuthority()).issuedAt(Instant.now()).build()).toList());
    }
}
