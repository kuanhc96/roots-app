package com.roots.authserver.component;

import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.roots.authserver.principal.MfaAuthenticationToken;
import com.roots.authserver.principal.MfaPendingAuthenticationToken;
import com.roots.authserver.service.UserCredentialService;

public class MfaAwareRememberMeAuthenticationProvider extends RememberMeAuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final UserCredentialService userCredentialService;

    public MfaAwareRememberMeAuthenticationProvider(String rememberMeKey, UserDetailsService userDetailsService, UserCredentialService userCredentialService) {
        super(rememberMeKey);
        this.userDetailsService = userDetailsService;
        this.userCredentialService = userCredentialService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (null != super.authenticate(authentication)) {
            String username = authentication.getName();
            UserDetails user = userDetailsService.loadUserByUsername(username);
            if (userCredentialService.isMfaEnabled(username)) {
                return new MfaPendingAuthenticationToken(user);
            }
            return new MfaAuthenticationToken(user, user.getAuthorities());
        }
        return null;
    }
}
