package com.roots.authserver.service;

import static com.roots.authserver.AuthServerConstants.DEFAULT_ROLE;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import com.roots.authserver.validator.Validator;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserCredentialService {

    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String ALL = UPPER + LOWER + DIGITS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserCredentialRepository userCredentialRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    @Transactional
    public CreateAccountResponse createAccount(CreateAccountRequest request) {
        validator.validateCreateAccountRequest(request);

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
                false,
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

    public boolean isPasswordChangeRequired(String email) {
        return userCredentialRepository.findByEmail(email)
                .map(uc -> uc.passwordChangeRequired())
                .orElse(false);
    }

    public void disableMfa(String email) {
        userCredentialRepository.setMfaEnabled(email, false);
    }

    public void verifyEmail(String email) {
        userCredentialRepository.verifyEmail(email);
    }

    /**
     * Issues a temporary password for a forgot-password request. If the email matches an
     * account, the password column is overwritten with a freshly generated temporary password
     * and {@code is_password_change_required} is set to true so the next login forces a reset.
     * Returns the plaintext temporary password (for emailing), or {@code null} if no account
     * matches — the caller returns the same response either way so account existence isn't leaked.
     */
    @Transactional
    public String requestTempPassword(String email) {
        if (userCredentialRepository.findByEmail(email).isEmpty()) {
            return null;
        }
        String tempPassword = generateTempPassword();
        userCredentialRepository.updatePassword(email, passwordEncoder.encode(tempPassword));
        userCredentialRepository.setPasswordChangeRequired(email, true);
        return tempPassword;
    }

    /**
     * Completes the forgot-password flow: stores the user's chosen new password and clears the
     * password-change flag. Also marks the email verified — successfully using a temporary
     * password emailed to the address proves ownership of it.
     */
    @Transactional
    public void completePasswordReset(String email, String newPassword) {
        validator.validatePassword(newPassword);
        userCredentialRepository.updatePassword(email, passwordEncoder.encode(newPassword));
        userCredentialRepository.setPasswordChangeRequired(email, false);
        userCredentialRepository.verifyEmail(email);
    }

    private String generateTempPassword() {
        List<Character> chars = new ArrayList<>(TEMP_PASSWORD_LENGTH);
        // Guarantee the temp password satisfies the same complexity rules as account creation.
        chars.add(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        chars.add(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        chars.add(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        for (int i = chars.size(); i < TEMP_PASSWORD_LENGTH; i++) {
            chars.add(ALL.charAt(RANDOM.nextInt(ALL.length())));
        }
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        chars.forEach(sb::append);
        return sb.toString();
    }
}
