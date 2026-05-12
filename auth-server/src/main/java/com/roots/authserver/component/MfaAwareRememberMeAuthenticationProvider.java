package com.roots.authserver.component;

import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.roots.authserver.principal.MfaPendingAuthenticationToken;

public class MfaAwareRememberMeAuthenticationProvider extends RememberMeAuthenticationProvider {

    private final UserDetailsService userDetailsService;

    public MfaAwareRememberMeAuthenticationProvider(String rememberMeKey, UserDetailsService userDetailsService) {
        super(rememberMeKey);
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (null != super.authenticate(authentication))  {
            String username = authentication.getName();
            UserDetails user = userDetailsService.loadUserByUsername(username);
            return new MfaPendingAuthenticationToken(user);
        }
        return null;
    }
}
