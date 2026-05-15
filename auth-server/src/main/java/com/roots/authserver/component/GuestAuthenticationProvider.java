package com.roots.authserver.component;

import com.roots.authserver.principal.GuestAuthenticationToken;
import com.roots.authserver.principal.MfaAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class GuestAuthenticationProvider implements AuthenticationProvider {

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        UserDetails guestUser = User.builder()
                .username("guest")
                .password("")
                .authorities("GUEST")
                .build();
        return new MfaAuthenticationToken(guestUser, guestUser.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return GuestAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
