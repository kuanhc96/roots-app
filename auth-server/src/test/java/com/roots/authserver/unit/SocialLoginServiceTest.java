package com.roots.authserver.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.GeneralSecurityException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.roots.authserver.enums.SocialProvider;
import com.roots.authserver.exception.SocialLoginException;
import com.roots.authserver.model.SocialBinding;
import com.roots.authserver.model.UserCredential;
import com.roots.authserver.repository.RoleRepository;
import com.roots.authserver.repository.SocialBindingRepository;
import com.roots.authserver.repository.UserCredentialRepository;
import com.roots.authserver.service.SocialLoginService;

@ExtendWith(MockitoExtension.class)
class SocialLoginServiceTest {

    private static final String ID_TOKEN = "id-token-string";
    private static final String SUB = "110169484474386276334";
    private static final String EMAIL = "socialuser@example.com";
    private static final String NAME = "Social User";

    @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;
    @Mock private UserCredentialRepository userCredentialRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private SocialBindingRepository socialBindingRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private GoogleIdToken googleIdToken;

    private SocialLoginService socialLoginService;

    @BeforeEach
    void setUp() {
        socialLoginService = new SocialLoginService(
                googleIdTokenVerifier, userCredentialRepository, roleRepository,
                socialBindingRepository, passwordEncoder);
    }

    private GoogleIdToken.Payload stubVerifiedToken(String sub, String email, Boolean emailVerified) throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(sub);
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        payload.set("name", NAME);
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);
        return payload;
    }

    private static UserCredential credential(long id, String email) {
        return new UserCredential(id, "guid-" + id, email, NAME, "{bcrypt}stored", true, true, false);
    }

    @Test
    void existingBinding_logsInBoundUser_withoutWriting() throws Exception {
        stubVerifiedToken(SUB, EMAIL, true);
        when(socialBindingRepository.findBySocialUserId(SUB))
                .thenReturn(Optional.of(new SocialBinding(7L, 42L, SocialProvider.GOOGLE, SUB)));
        when(userCredentialRepository.findById(42L)).thenReturn(Optional.of(credential(42L, EMAIL)));

        String result = socialLoginService.loginWithGoogle(ID_TOKEN);

        assertThat(result).isEqualTo(EMAIL);
        verify(userCredentialRepository, never()).insert(any());
        verify(roleRepository, never()).insert(anyLong(), anyString());
        verify(socialBindingRepository, never()).insert(anyLong(), any(), anyString());
    }

    @Test
    void existingBinding_subWinsOverChangedGoogleEmail() throws Exception {
        // The Google-side email changed since binding; the bound local account still wins.
        stubVerifiedToken(SUB, "renamed@example.com", true);
        when(socialBindingRepository.findBySocialUserId(SUB))
                .thenReturn(Optional.of(new SocialBinding(7L, 42L, SocialProvider.GOOGLE, SUB)));
        when(userCredentialRepository.findById(42L)).thenReturn(Optional.of(credential(42L, EMAIL)));

        String result = socialLoginService.loginWithGoogle(ID_TOKEN);

        assertThat(result).isEqualTo(EMAIL);
        verify(userCredentialRepository, never()).insert(any());
        verify(socialBindingRepository, never()).insert(anyLong(), any(), anyString());
    }

    @Test
    void noBinding_existingEmail_linksSubToExistingAccount() throws Exception {
        stubVerifiedToken(SUB, EMAIL, true);
        when(socialBindingRepository.findBySocialUserId(SUB))
                .thenReturn(Optional.empty());
        when(userCredentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(credential(42L, EMAIL)));

        String result = socialLoginService.loginWithGoogle(ID_TOKEN);

        assertThat(result).isEqualTo(EMAIL);
        verify(socialBindingRepository).insert(42L, SocialProvider.GOOGLE, SUB);
        verify(userCredentialRepository, never()).insert(any());
        verify(roleRepository, never()).insert(anyLong(), anyString());
    }

    @Test
    void noBinding_unknownEmail_createsAccountRoleAndBinding() throws Exception {
        stubVerifiedToken(SUB, EMAIL, true);
        when(socialBindingRepository.findBySocialUserId(SUB))
                .thenReturn(Optional.empty());
        when(userCredentialRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}unusable");
        when(userCredentialRepository.insert(any())).thenReturn(99L);

        String result = socialLoginService.loginWithGoogle(ID_TOKEN);

        assertThat(result).isEqualTo(EMAIL);

        ArgumentCaptor<UserCredential> captor = ArgumentCaptor.forClass(UserCredential.class);
        verify(userCredentialRepository).insert(captor.capture());
        UserCredential created = captor.getValue();
        assertThat(created.email()).isEqualTo(EMAIL);
        assertThat(created.name()).isEqualTo(NAME);
        assertThat(created.password()).isEqualTo("{bcrypt}unusable");
        assertThat(created.mfaEnabled()).isTrue();
        assertThat(created.emailVerified()).isTrue();
        assertThat(created.passwordChangeRequired()).isFalse();
        assertThat(created.userGuid()).isNotBlank();

        verify(roleRepository).insert(99L, "member");
        verify(socialBindingRepository).insert(99L, SocialProvider.GOOGLE, SUB);
    }

    @Test
    void noBinding_unknownEmail_missingNameFallsBackToEmail() throws Exception {
        GoogleIdToken.Payload payload = stubVerifiedToken(SUB, EMAIL, true);
        payload.remove("name");
        when(socialBindingRepository.findBySocialUserId(SUB))
                .thenReturn(Optional.empty());
        when(userCredentialRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}unusable");
        when(userCredentialRepository.insert(any())).thenReturn(99L);

        socialLoginService.loginWithGoogle(ID_TOKEN);

        ArgumentCaptor<UserCredential> captor = ArgumentCaptor.forClass(UserCredential.class);
        verify(userCredentialRepository).insert(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo(EMAIL);
    }

    @Test
    void unverifiedGoogleEmail_isRejected_beforeAnyLookup() throws Exception {
        stubVerifiedToken(SUB, EMAIL, false);

        assertThatThrownBy(() -> socialLoginService.loginWithGoogle(ID_TOKEN))
                .isInstanceOf(SocialLoginException.class)
                .hasMessageContaining("unverified");

        verify(socialBindingRepository, never()).findBySocialUserId(anyString());
        verify(userCredentialRepository, never()).insert(any());
        verify(socialBindingRepository, never()).insert(anyLong(), any(), anyString());
    }

    @Test
    void missingEmailClaim_isRejected() throws Exception {
        stubVerifiedToken(SUB, null, true);

        assertThatThrownBy(() -> socialLoginService.loginWithGoogle(ID_TOKEN))
                .isInstanceOf(SocialLoginException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void invalidToken_isRejected() throws Exception {
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(null);

        assertThatThrownBy(() -> socialLoginService.loginWithGoogle(ID_TOKEN))
                .isInstanceOf(SocialLoginException.class)
                .hasMessageContaining("failed verification");
    }

    @Test
    void verifierError_isWrapped() throws Exception {
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenThrow(new GeneralSecurityException("boom"));

        assertThatThrownBy(() -> socialLoginService.loginWithGoogle(ID_TOKEN))
                .isInstanceOf(SocialLoginException.class)
                .hasCauseInstanceOf(GeneralSecurityException.class);
    }
}
