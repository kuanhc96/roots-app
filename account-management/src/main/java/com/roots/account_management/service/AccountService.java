package com.roots.account_management.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roots.account_management.dto.request.CreateAccountRequest;
import com.roots.account_management.dto.response.AccountProfileResponse;
import com.roots.account_management.dto.response.AccountProfilesResponse;
import com.roots.account_management.dto.response.CreateTestAccountResponse;
import com.roots.account_management.dto.response.UpdateMfaResponse;
import com.roots.account_management.enums.Role;
import com.roots.account_management.exception.EmailAlreadyExistsException;
import com.roots.account_management.exception.UserCredentialNotFoundException;
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
    public CreateTestAccountResponse createTestAccount(CreateAccountRequest request) {
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

        return new CreateTestAccountResponse(
                request.name(),
                request.email(),
                userGUID,
                request.mfaEnabled(),
                request.emailVerified(),
                request.passwordChangeRequired(),
                roles
        );
    }

    public UserCredential getUserCredentialByEmail(String email) throws UserCredentialNotFoundException {
        return userCredentialRepository.findByEmail(email)
                .orElseThrow(() -> new UserCredentialNotFoundException(
                        "No account found for email " + email));
    }

    public UserCredential getUserCredentialByUserGUID(String userGUID) throws UserCredentialNotFoundException {
        return userCredentialRepository.findByUserGUID(userGUID)
                .orElseThrow(() -> new UserCredentialNotFoundException(
                        "No account found for userGUID " + userGUID));
    }

    @Transactional(readOnly = true)
    public AccountProfilesResponse getAccountProfiles(int page, int size) {
        int offset = page * size;
        List<AccountProfileResponse> accounts = userCredentialRepository.findAllPaged(size, offset)
                .stream()
                .map(AccountProfileResponse::from)
                .toList();
        long totalElements = userCredentialRepository.countAll();

        return new AccountProfilesResponse(page, size, totalElements, accounts);
    }

    @Transactional(readOnly = true)
    public List<AccountProfileResponse> searchAccountProfiles(String email, String name, boolean fullMatch, int maxCount) {
        List<UserCredential> credentials = (email != null && !email.isBlank())
                ? userCredentialRepository.searchByEmail(email, fullMatch, maxCount)
                : userCredentialRepository.searchByName(name, fullMatch, maxCount);

        return credentials.stream().map(AccountProfileResponse::from).toList();
    }

    @Transactional
    public void deleteAccountsByUserGUIDs(List<String> userGUIDs) {
        userGUIDs.stream()
                .map(String::trim)
                .distinct()
                .forEach(this::deleteTestAccountByUserGUID);
    }

    @Transactional
    public UpdateMfaResponse updateMfaEnabledByUserGUID(String userGUID, boolean mfaEnabled)
            throws UserCredentialNotFoundException {
        UserCredential userCredential = getUserCredentialByUserGUID(userGUID);
        userCredentialRepository.setMfaEnabledByUserGUID(userCredential.id(), mfaEnabled);
        return UpdateMfaResponse.builder().userGUID(userGUID).mfaEnabled(mfaEnabled).build();
    }

    @Transactional
    public void deleteTestAccountByEmail(String email) {
        userCredentialRepository.findByEmail(email).ifPresent(this::deleteAccount);
    }

    @Transactional
    public void deleteTestAccountByUserGUID(String userGUID) {
        userCredentialRepository.findByUserGUID(userGUID).ifPresent(this::deleteAccount);
    }

    private void deleteAccount(UserCredential credential) {
        long credentialId = credential.id();
        roleRepository.deleteByCredentialId(credentialId);
        userCredentialRepository.deleteById(credentialId);
    }

    private List<Role> resolveRoles(List<Role> requested) {
        Set<Role> roles = new LinkedHashSet<>();
        roles.add(DEFAULT_ROLE);
        if (requested != null) {
            roles.addAll(requested);
        }
        return List.copyOf(roles);
    }
}
