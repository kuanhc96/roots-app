package com.roots.authserver.service;

import org.springframework.stereotype.Service;

import com.roots.authserver.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserCredentialService {

    private final UserCredentialRepository userCredentialRepository;

    public boolean isMfaEnabled(String email) {
        return userCredentialRepository.findByEmail(email)
                .map(uc -> uc.mfaEnabled())
                .orElse(true);
    }

    public void disableMfa(String email) {
        userCredentialRepository.setMfaEnabled(email, false);
    }
}
