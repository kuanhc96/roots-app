package com.roots.authserver.component;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.roots.authserver.principal.MfaPendingAuthenticationToken;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MfaAwareDaoAuthenticationProvider implements AuthenticationProvider {
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = authentication.getName();
        String presented = authentication.getCredentials().toString();
        UserDetails user = userDetailsService.loadUserByUsername(username);

        if (!passwordEncoder.matches(presented, user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return new MfaPendingAuthenticationToken(user);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
