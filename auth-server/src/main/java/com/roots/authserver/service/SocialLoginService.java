package com.roots.authserver.service;

import static com.roots.authserver.AuthServerConstants.DEFAULT_ROLE;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.roots.authserver.dto.response.GoogleTokenResponse;
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
    private static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final UserCredentialRepository userCredentialRepository;
    private final RoleRepository roleRepository;
    private final SocialBindingRepository socialBindingRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestClient restClient;

    // Field injection (not constructor args) so @RequiredArgsConstructor keeps wiring
    // the final dependencies (same pattern as EmailService).
    @Value("${google.client-id}")
    private String googleClientId;
    @Value("${google.client-secret}")
    private String googleClientSecret;

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
    /**
     * Exchanges a Google authorization {@code code} for tokens (server-to-server, using
     * the client secret — never exposed to the browser), then resolves the returned
     * id_token to a local account exactly as {@link #loginWithGoogle(String)}.
     *
     * @param redirectUri must byte-for-byte match the one sent on the authorize request
     *                    (and a URI registered in Google Cloud Console)
     * @throws SocialLoginException if the exchange fails or returns no id_token, or if
     *                              the resulting id_token itself fails verification
     */
    @Transactional
    public String loginWithGoogleCode(String code, String redirectUri) {
        GoogleTokenResponse tokens = exchangeCode(code, redirectUri);
        if (tokens == null || StringUtils.isBlank(tokens.idToken())) {
            throw new SocialLoginException("Google authorization-code exchange returned no id_token");
        }
        return loginWithGoogle(tokens.idToken());
    }

    private GoogleTokenResponse exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        try {
            return restClient.post()
                    .uri(GOOGLE_TOKEN_ENDPOINT)
                    .headers(headers -> headers.setBasicAuth(googleClientId, googleClientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (RestClientException e) {
            throw new SocialLoginException("Google authorization-code exchange failed", e);
        }
    }

    @Transactional
    public String loginWithGoogle(String idTokenString) {
        GoogleIdToken.Payload payload = verify(idTokenString);

        String sub = payload.getSubject();
        String email = payload.getEmail();
        if (StringUtils.isAnyBlank(sub, email)) {
            throw new SocialLoginException("Google id_token is missing the sub or email claim");
        }
        // Only a Google-verified email may create or link a local account: an attacker
        // must not be able to bind their Google identity to a victim's existing account
        // by claiming (but never proving) the victim's address at Google.
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new SocialLoginException("Google reports the email as unverified: " + email);
        }

        var binding = socialBindingRepository.findBySocialUserId(sub);
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
