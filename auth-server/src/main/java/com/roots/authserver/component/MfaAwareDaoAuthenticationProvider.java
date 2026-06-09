package com.roots.authserver.component;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.roots.authserver.principal.CreateAccountPendingAuthenticationToken;
import com.roots.authserver.principal.MfaAuthenticationToken;
import com.roots.authserver.principal.MfaPendingAuthenticationToken;
import com.roots.authserver.service.UserCredentialService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MfaAwareDaoAuthenticationProvider implements AuthenticationProvider {
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final UserCredentialService userCredentialService;

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = authentication.getName();
        String presented = authentication.getCredentials().toString();
        UserDetails user = userDetailsService.loadUserByUsername(username);

        if (!passwordEncoder.matches(presented, user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!userCredentialService.isEmailVerified(username)) {
            return new CreateAccountPendingAuthenticationToken(user);
        }

        if (userCredentialService.isMfaEnabled(username)) {
            return new MfaPendingAuthenticationToken(user);
        }
        return new MfaAuthenticationToken(user, user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
