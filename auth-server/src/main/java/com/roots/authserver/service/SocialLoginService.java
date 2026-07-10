package com.roots.authserver.service;

import static com.roots.authserver.AuthServerConstants.DEFAULT_ROLE;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.roots.authserver.enums.SocialProvider;
import com.roots.authserver.exception.SocialLoginException;
import com.roots.authserver.model.UserCredential;
import com.roots.authserver.repository.RoleRepository;
import com.roots.authserver.repository.SocialBindingRepository;
import com.roots.authserver.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final UserCredentialRepository userCredentialRepository;
    private final RoleRepository roleRepository;
    private final SocialBindingRepository socialBindingRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Verifies a Google id_token and resolves it to a local account, creating the
     * account and/or the social binding as needed. Returns the local account's email
     * (the login username) for the controller to build the authenticated session from.
     *
     * <p>Resolution is <b>sub-first</b>: Google's {@code sub} is the stable identifier,
     * while the Google-side email can be changed by the user. An existing
     * {@code social_binding} row therefore wins over the token's email; the email is
     * only consulted when no binding exists — to link an existing local account or to
     * create a new one.
     *
     * @throws SocialLoginException if the token fails verification, the Google email is
     *                              not verified, or required claims are missing
     */
    @Transactional
    public String loginWithGoogle(String idTokenString) {
        GoogleIdToken.Payload payload = verify(idTokenString);

        String sub = payload.getSubject();
        String email = payload.getEmail();
        if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
            throw new SocialLoginException("Google id_token is missing the sub or email claim");
        }
        // Only a Google-verified email may create or link a local account: an attacker
        // must not be able to bind their Google identity to a victim's existing account
        // by claiming (but never proving) the victim's address at Google.
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new SocialLoginException("Google reports the email as unverified: " + email);
        }

        var binding = socialBindingRepository.findByProviderAndSocialUserId(SocialProvider.GOOGLE, sub);
        if (binding.isPresent()) {
            UserCredential bound = userCredentialRepository.findById(binding.get().userId())
                    .orElseThrow(() -> new SocialLoginException(
                            "Social binding %d references a missing user credential".formatted(binding.get().id())));
            if (!bound.email().equals(email)) {
                // The Google account's email changed since it was bound; sub is the
                // identity key, so the bound local account still wins.
                log.warn("Google sub is bound to local account {} but the id_token now carries email {}",
                        bound.email(), email);
            }
            return bound.email();
        }

        long credentialId = userCredentialRepository.findByEmail(email)
                .map(UserCredential::id)
                .orElseGet(() -> createAccountFromGoogle(email, payload));
        socialBindingRepository.insert(credentialId, SocialProvider.GOOGLE, sub);
        return email;
    }

    /**
     * Auto-creates a local account for a first-time Google login: email verified (Google
     * vouched for it), MFA flag on (only meaningful for password logins), and a random
     * unusable password — never revealed, so a password login attempt fails exactly like
     * any wrong password. The user can set a real one via the forgot-password flow, which
     * also owns the {@code is_password_change_required} flag (false here).
     */
    private long createAccountFromGoogle(String email, GoogleIdToken.Payload payload) {
        String name = payload.get("name") instanceof String s && !s.isBlank() ? s : email;

        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String unusablePassword = Base64.getEncoder().encodeToString(randomBytes);

        UserCredential userCredential = new UserCredential(
                null,
                UUID.randomUUID().toString(),
                email,
                name,
                passwordEncoder.encode(unusablePassword),
                true,
                true,
                false
        );

        long credentialId = userCredentialRepository.insert(userCredential);
        roleRepository.insert(credentialId, DEFAULT_ROLE);
        return credentialId;
    }

    private GoogleIdToken.Payload verify(String idTokenString) {
        GoogleIdToken idToken;
        try {
            idToken = googleIdTokenVerifier.verify(idTokenString);
        } catch (Exception e) {
            throw new SocialLoginException("Google id_token verification errored", e);
        }
        if (idToken == null) {
            throw new SocialLoginException("Google id_token failed verification");
        }
        return idToken.getPayload();
    }
}
