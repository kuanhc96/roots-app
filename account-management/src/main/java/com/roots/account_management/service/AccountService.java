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
import com.roots.account_management.validator.CreateAccountValidator;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final Role DEFAULT_ROLE = Role.MEMBER;

    private final UserCredentialRepository userCredentialRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CreateAccountValidator createAccountValidator;

    @Transactional
    public CreateAccountResponse createTestAccount(CreateAccountRequest request) {
        createAccountValidator.validate(request);

        if (userCredentialRepository.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        String userGuid = UUID.randomUUID().toString();

        UserCredential userCredential = new UserCredential(
                null,
                userGuid,
                request.email(),
                request.name(),
                passwordEncoder.encode(request.password()),
                request.mfaEnabled(),
                request.emailVerified()
        );

        long credentialId = userCredentialRepository.insert(userCredential);

        List<Role> roles = resolveRoles(request.roles());
        for (Role role : roles) {
            roleRepository.insert(credentialId, role.dbValue());
        }

        return new CreateAccountResponse(
                request.name(),
                request.email(),
                userGuid,
                request.mfaEnabled(),
                request.emailVerified(),
                roles
        );
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
