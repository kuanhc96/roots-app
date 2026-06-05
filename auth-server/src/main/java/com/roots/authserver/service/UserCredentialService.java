package com.roots.authserver.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roots.authserver.dto.request.CreateAccountRequest;
import com.roots.authserver.dto.response.CreateAccountResponse;
import com.roots.authserver.exception.EmailAlreadyExistsException;
import com.roots.authserver.model.UserCredential;
import com.roots.authserver.repository.RoleRepository;
import com.roots.authserver.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserCredentialService {

    private static final String DEFAULT_ROLE = "member";

    private final UserCredentialRepository userCredentialRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CreateAccountValidator createAccountValidator;

    @Transactional
    public CreateAccountResponse createAccount(CreateAccountRequest request) {
        createAccountValidator.validate(request);

        if (userCredentialRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        UserCredential userCredential = new UserCredential(
                null,
                UUID.randomUUID().toString(),
                request.email(),
                request.name(),
                passwordEncoder.encode(request.password()),
                true,
                false
        );

        long credentialId = userCredentialRepository.insert(userCredential);
        roleRepository.insert(credentialId, DEFAULT_ROLE);

        return new CreateAccountResponse(request.name(), request.email());
    }

    public boolean isMfaEnabled(String email) {
        return userCredentialRepository.findByEmail(email)
                .map(uc -> uc.mfaEnabled())
                .orElse(true);
    }

    public boolean isEmailVerified(String email) {
        return userCredentialRepository.findByEmail(email)
                .map(uc -> uc.emailVerified())
                .orElse(false);
    }

    public void disableMfa(String email) {
        userCredentialRepository.setMfaEnabled(email, false);
    }

    public void verifyEmail(String email) {
        userCredentialRepository.verifyEmail(email);
    }
}
