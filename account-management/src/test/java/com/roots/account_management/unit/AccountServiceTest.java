package com.roots.account_management.unit;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.roots.account_management.dto.request.CreateAccountRequest;
import com.roots.account_management.dto.response.CreateTestAccountResponse;
import com.roots.account_management.enums.Role;
import com.roots.account_management.exception.EmailAlreadyExistsException;
import com.roots.account_management.model.UserCredential;
import com.roots.account_management.repository.RoleRepository;
import com.roots.account_management.repository.UserCredentialRepository;
import com.roots.account_management.service.AccountService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final long CREDENTIAL_ID = 42L;

    @Mock
    private UserCredentialRepository userCredentialRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    @Test
    void createTestAccount_persistsEncodedCredentialAndRoles_andReturnsResponse() {
        CreateAccountRequest request = new CreateAccountRequest(
                "Jane", "jane@example.com", "Password123", false, true, true, List.of(Role.PASTOR));
        when(userCredentialRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password123")).thenReturn("ENCODED");
        when(userCredentialRepository.insert(any(UserCredential.class))).thenReturn(CREDENTIAL_ID);

        CreateTestAccountResponse response = accountService.createTestAccount(request);

        // Credential is inserted with the hashed password, the request flags, and a generated guid.
        ArgumentCaptor<UserCredential> captor = ArgumentCaptor.forClass(UserCredential.class);
        verify(userCredentialRepository).insert(captor.capture());
        UserCredential inserted = captor.getValue();
        assertThat(inserted.id()).isNull();
        assertThat(inserted.email()).isEqualTo("jane@example.com");
        assertThat(inserted.name()).isEqualTo("Jane");
        assertThat(inserted.password()).isEqualTo("ENCODED");
        assertThat(inserted.mfaEnabled()).isFalse();
        assertThat(inserted.emailVerified()).isTrue();
        assertThat(inserted.passwordChangeRequired()).isTrue();
        assertThat(inserted.userGUID()).isNotBlank();

        // MEMBER floor first, then the requested PASTOR.
        verify(roleRepository).insert(CREDENTIAL_ID, "member");
        verify(roleRepository).insert(CREDENTIAL_ID, "pastor");

        // Response mirrors the request and carries the same generated guid that was persisted.
        assertThat(response.name()).isEqualTo("Jane");
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.userGUID()).isEqualTo(inserted.userGUID());
        assertThat(response.mfaEnabled()).isFalse();
        assertThat(response.emailVerified()).isTrue();
        assertThat(response.passwordChangeRequired()).isTrue();
        assertThat(response.roles()).containsExactly(Role.MEMBER, Role.PASTOR);
    }

    @Test
    void createTestAccount_withNullRoles_assignsOnlyMember_andDefaultsFlags() {
        // mfaEnabled/emailVerified/passwordChangeRequired null -> the record's compact
        // constructor applies defaults (true/false/false).
        CreateAccountRequest request = new CreateAccountRequest(
                "Jane", "jane@example.com", "Password123", null, null, null, null);
        when(userCredentialRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("ENCODED");
        when(userCredentialRepository.insert(any(UserCredential.class))).thenReturn(CREDENTIAL_ID);

        CreateTestAccountResponse response = accountService.createTestAccount(request);

        assertThat(response.roles()).containsExactly(Role.MEMBER);
        assertThat(response.mfaEnabled()).isTrue();
        assertThat(response.emailVerified()).isFalse();
        assertThat(response.passwordChangeRequired()).isFalse();
        verify(roleRepository).insert(CREDENTIAL_ID, "member");
        verify(roleRepository, times(1)).insert(anyLong(), anyString());
    }

    @Test
    void createTestAccount_deduplicatesRolesPreservingOrder() {
        CreateAccountRequest request = new CreateAccountRequest(
                "Jane", "jane@example.com", "Password123", true, false, false,
                List.of(Role.DEACON, Role.MEMBER, Role.DEACON));
        when(userCredentialRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("ENCODED");
        when(userCredentialRepository.insert(any(UserCredential.class))).thenReturn(CREDENTIAL_ID);

        CreateTestAccountResponse response = accountService.createTestAccount(request);

        // MEMBER (floor) is added first, then DEACON; the duplicate DEACON is dropped.
        assertThat(response.roles()).containsExactly(Role.MEMBER, Role.DEACON);
    }

    @Test
    void createTestAccount_withExistingEmail_throwsAndDoesNotPersist() {
        CreateAccountRequest request = new CreateAccountRequest(
                "Jane", "jane@example.com", "Password123", true, false, false, List.of());
        UserCredential existing = new UserCredential(
                1L, "existing-guid", "jane@example.com", "Jane", "hash", true, true, false);
        when(userCredentialRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> accountService.createTestAccount(request))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userCredentialRepository, never()).insert(any());
        verifyNoInteractions(roleRepository, passwordEncoder);
    }

    @Test
    void deleteTestAccountByEmail_whenFound_deletesRolesThenCredential() {
        UserCredential credential = new UserCredential(
                7L, "guid", "jane@example.com", "Jane", "hash", true, true, false);
        when(userCredentialRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(credential));

        accountService.deleteTestAccountByEmail("jane@example.com");

        // Role rows must be removed before the credential (the role FK has no ON DELETE CASCADE).
        InOrder inOrder = inOrder(roleRepository, userCredentialRepository);
        inOrder.verify(roleRepository).deleteByCredentialId(7L);
        inOrder.verify(userCredentialRepository).deleteById(7L);
    }

    @Test
    void deleteTestAccountByEmail_whenNotFound_isNoOp() {
        when(userCredentialRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        accountService.deleteTestAccountByEmail("missing@example.com");

        verify(roleRepository, never()).deleteByCredentialId(anyLong());
        verify(userCredentialRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteTestAccountByUserGUID_whenFound_deletesRolesThenCredential() {
        UserCredential credential = new UserCredential(
                9L, "the-guid", "jane@example.com", "Jane", "hash", true, true, false);
        when(userCredentialRepository.findByUserGUID("the-guid")).thenReturn(Optional.of(credential));

        accountService.deleteTestAccountByUserGUID("the-guid");

        InOrder inOrder = inOrder(roleRepository, userCredentialRepository);
        inOrder.verify(roleRepository).deleteByCredentialId(9L);
        inOrder.verify(userCredentialRepository).deleteById(9L);
    }

    @Test
    void deleteTestAccountByUserGUID_whenNotFound_isNoOp() {
        when(userCredentialRepository.findByUserGUID("missing-guid")).thenReturn(Optional.empty());

        accountService.deleteTestAccountByUserGUID("missing-guid");

        verify(roleRepository, never()).deleteByCredentialId(anyLong());
        verify(userCredentialRepository, never()).deleteById(anyLong());
    }
}
