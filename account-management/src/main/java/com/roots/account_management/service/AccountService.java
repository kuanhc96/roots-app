package com.roots.account_management.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roots.account_management.dto.request.CreateAccountRequest;
import com.roots.account_management.dto.response.CreateAccountResponse;
import com.roots.account_management.enums.Role;
import com.roots.account_management.exception.EmailAlreadyExistsException;
import com.roots.account_management.model.UserCredential;
import com.roots.account_management.repository.RoleRepository;
import com.roots.account_management.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final Role DEFAULT_ROLE = Role.MEMBER;

    private final UserCredentialRepository userCredentialRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CreateAccountResponse createTestAccount(CreateAccountRequest request) {
        if (userCredentialRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        String userGUID = UUID.randomUUID().toString();

        UserCredential userCredential = new UserCredential(
                null,
                userGUID,
                request.email(),
                request.name(),
                passwordEncoder.encode(request.password()),
                request.mfaEnabled(),
                request.emailVerified(),
                request.passwordChangeRequired()
        );

        long credentialId = userCredentialRepository.insert(userCredential);

        List<Role> roles = resolveRoles(request.roles());
        for (Role role : roles) {
            roleRepository.insert(credentialId, role.getValue());
        }

        return new CreateAccountResponse(
                request.name(),
                request.email(),
                userGUID,
                request.mfaEnabled(),
                request.emailVerified(),
                request.passwordChangeRequired(),
                roles
        );
    }

    // Idempotent: no match is a no-op so test teardown can run safely more than once.
    @Transactional
    public void deleteTestAccountByEmail(String email) {
        userCredentialRepository.findByEmail(email).ifPresent(this::deleteAccount);
    }

    // Idempotent: no match is a no-op so test teardown can run safely more than once.
    @Transactional
    public void deleteTestAccountByUserGUID(String userGUID) {
        userCredentialRepository.findByUserGUID(userGUID).ifPresent(this::deleteAccount);
    }

    // Role rows are removed before the credential because the role FK has no
    // ON DELETE CASCADE.
    private void deleteAccount(UserCredential credential) {
        long credentialId = credential.id();
        roleRepository.deleteByCredentialId(credentialId);
        userCredentialRepository.deleteById(credentialId);
    }

    // MEMBER is always present (the floor), then any caller-requested roles, de-duplicated
    // while preserving insertion order.
    private List<Role> resolveRoles(List<Role> requested) {
        Set<Role> roles = new LinkedHashSet<>();
        roles.add(DEFAULT_ROLE);
        if (requested != null) {
            roles.addAll(requested);
        }
        return List.copyOf(roles);
    }
}
