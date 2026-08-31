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
import com.roots.account_management.dto.response.AddRoleResponse;
import com.roots.account_management.dto.response.AccountProfileResponse;
import com.roots.account_management.dto.response.AccountProfilesResponse;
import com.roots.account_management.dto.response.CreateTestAccountResponse;
import com.roots.account_management.dto.response.UpdateEmailResponse;
import com.roots.account_management.dto.response.UpdateMfaResponse;
import com.roots.account_management.dto.response.UpdateNameResponse;
import com.roots.account_management.dto.response.UpdatePasswordResponse;
import com.roots.account_management.enums.Role;
import com.roots.account_management.exception.EmailAlreadyExistsException;
import com.roots.account_management.exception.UserCredentialNotFoundException;
import com.roots.account_management.model.UserCredential;
import com.roots.account_management.repository.RoleRepository;
import com.roots.account_management.repository.UserCredentialRepository;
import com.roots.account_management.service.AccountService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

        verify(roleRepository).insert(CREDENTIAL_ID, "member");
        verify(roleRepository).insert(CREDENTIAL_ID, "pastor");

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

    @Test
    void deleteAccountsByUserGUIDs_deduplicatesAndTrimsValues() {
        UserCredential first = new UserCredential(
                11L, "guid-1", "one@example.com", "One", "hash", true, true, false);
        UserCredential second = new UserCredential(
                12L, "guid-2", "two@example.com", "Two", "hash", true, true, false);

        when(userCredentialRepository.findByUserGUID("guid-1")).thenReturn(Optional.of(first));
        when(userCredentialRepository.findByUserGUID("guid-2")).thenReturn(Optional.of(second));
        when(userCredentialRepository.findByUserGUID("missing-guid")).thenReturn(Optional.empty());

        accountService.deleteAccountsByUserGUIDs(List.of("guid-1", " guid-1 ", "guid-2", "missing-guid"));

        verify(userCredentialRepository).findByUserGUID("guid-1");
        verify(userCredentialRepository).findByUserGUID("guid-2");
        verify(userCredentialRepository).findByUserGUID("missing-guid");
        verify(roleRepository).deleteByCredentialId(11L);
        verify(roleRepository).deleteByCredentialId(12L);
        verify(userCredentialRepository).deleteById(11L);
        verify(userCredentialRepository).deleteById(12L);
    }

    @Test
    void getAccountProfiles_returnsPageResponseWithMappedAccounts() {
        UserCredential first = new UserCredential(1L, "guid-1", "one@example.com", "One", "hash", true, false, false);
        UserCredential second = new UserCredential(2L, "guid-2", "two@example.com", "Two", "hash", false, true, false);
        when(userCredentialRepository.findAllPaged(20, 40)).thenReturn(List.of(first, second));
        when(userCredentialRepository.countAll()).thenReturn(55L);

        AccountProfilesResponse response = accountService.getAccountProfiles(2, 20);

        verify(userCredentialRepository).findAllPaged(20, 40);
        verify(userCredentialRepository).countAll();
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(55L);
        assertThat(response.accounts()).hasSize(2);
        assertThat(response.accounts().get(0).userGUID()).isEqualTo("guid-1");
        assertThat(response.accounts().get(0).email()).isEqualTo("one@example.com");
        assertThat(response.accounts().get(0).name()).isEqualTo("One");
    }

    @Test
    void searchAccountProfiles_byEmail_usesRepositoryAndMapsResult() {
        UserCredential result = new UserCredential(1L, "guid-1", "jane@example.com", "Jane", "hash", true, false, false);
        when(userCredentialRepository.searchByEmail("jane@example.com", false, 100)).thenReturn(List.of(result));

        List<AccountProfileResponse> response = accountService.searchAccountProfiles("jane@example.com", null, false, 100);

        verify(userCredentialRepository).searchByEmail("jane@example.com", false, 100);
        assertThat(response).hasSize(1);
        assertThat(response.get(0).userGUID()).isEqualTo("guid-1");
        assertThat(response.get(0).email()).isEqualTo("jane@example.com");
        assertThat(response.get(0).name()).isEqualTo("Jane");
    }

    @Test
    void searchAccountProfiles_byName_usesRepositoryAndMapsResult() {
        UserCredential result = new UserCredential(2L, "guid-2", "john@example.com", "John", "hash", true, false, false);
        when(userCredentialRepository.searchByName("John", true, 5)).thenReturn(List.of(result));

        List<AccountProfileResponse> response = accountService.searchAccountProfiles(null, "John", true, 5);

        verify(userCredentialRepository).searchByName("John", true, 5);
        assertThat(response).hasSize(1);
        assertThat(response.get(0).userGUID()).isEqualTo("guid-2");
    }

    @Test
    void updateMfaEnabledByUserGUID_whenFound_updatesAndReturnsResponse() throws Exception {
        String userGUID = "guid-123";
        UserCredential updated = new UserCredential(
                5L, userGUID, "jane@example.com", "Jane", "hash", false, true, false);
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.of(updated));
        when(userCredentialRepository.setMfaEnabledByUserGUID(5L, false)).thenReturn(1);

        UpdateMfaResponse result = accountService.updateMfaEnabledByUserGUID(userGUID, false);

        verify(userCredentialRepository).findByUserGUID(userGUID);
        verify(userCredentialRepository).setMfaEnabledByUserGUID(5L, false);
        assertThat(result.userGUID()).isEqualTo(userGUID);
        assertThat(result.mfaEnabled()).isFalse();
    }

    @Test
    void updateMfaEnabledByUserGUID_whenNotFound_throws() {
        String userGUID = "missing-guid";
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateMfaEnabledByUserGUID(userGUID, true))
                .isInstanceOf(UserCredentialNotFoundException.class)
                .hasMessage("No account found for userGUID " + userGUID);

        verify(userCredentialRepository).findByUserGUID(userGUID);
        verify(userCredentialRepository, never()).setMfaEnabledByUserGUID(anyLong(), anyBoolean());
    }

    @Test
    void updatePasswordByUserGUID_whenFound_updatesAndReturnsResponse() throws Exception {
        String userGUID = "guid-123";
        UserCredential existing = new UserCredential(
                5L, userGUID, "jane@example.com", "Jane", "old-hash", true, true, false);
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("old-hash", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-hash");
        when(userCredentialRepository.setPasswordByUserGUID(5L, "new-hash")).thenReturn(1);

        UpdatePasswordResponse result = accountService.updatePasswordByUserGUID(userGUID, "NewPassword123", "old-hash");

        verify(userCredentialRepository).findByUserGUID(userGUID);
        verify(passwordEncoder).encode("NewPassword123");
        verify(userCredentialRepository).setPasswordByUserGUID(5L, "new-hash");
        assertThat(result.userGUID()).isEqualTo(userGUID);
    }

    @Test
    void updatePasswordByUserGUID_whenNotFound_throws() {
        String userGUID = "missing-guid";
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updatePasswordByUserGUID(userGUID, "NewPassword123", "old-hash"))
                .isInstanceOf(UserCredentialNotFoundException.class)
                .hasMessage("No account found for userGUID " + userGUID);

        verify(userCredentialRepository).findByUserGUID(userGUID);
        verify(userCredentialRepository, never()).setPasswordByUserGUID(anyLong(), anyString());
    }

    @Test
    void updateNameByUserGUID_whenFound_trimsUpdatesAndReturnsResponse() throws Exception {
        String userGUID = "guid-123";
        UserCredential existing = new UserCredential(
                5L, userGUID, "jane@example.com", "Jane", "hash", true, true, false);
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.of(existing));
        when(userCredentialRepository.setNameByUserGUID(5L, "Jane Doe")).thenReturn(1);

        UpdateNameResponse result = accountService.updateNameByUserGUID(userGUID, "  Jane Doe  ");

        verify(userCredentialRepository).findByUserGUID(userGUID);
        verify(userCredentialRepository).setNameByUserGUID(5L, "Jane Doe");
        assertThat(result.userGUID()).isEqualTo(userGUID);
        assertThat(result.name()).isEqualTo("Jane Doe");
    }

    @Test
    void updateNameByUserGUID_whenNotFound_throws() {
        String userGUID = "missing-guid";
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateNameByUserGUID(userGUID, "Jane Doe"))
                .isInstanceOf(UserCredentialNotFoundException.class)
                .hasMessage("No account found for userGUID " + userGUID);

        verify(userCredentialRepository).findByUserGUID(userGUID);
        verify(userCredentialRepository, never()).setNameByUserGUID(anyLong(), anyString());
    }

    @Test
    void updateEmailByUserGUID_whenFound_trimsUpdatesAndReturnsResponse() throws Exception {
        String userGUID = "guid-123";
        UserCredential existing = new UserCredential(
                5L, userGUID, "jane@example.com", "Jane", "hash", true, true, false);
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.of(existing));
        when(userCredentialRepository.findByEmail("updated@example.com")).thenReturn(Optional.empty());
        when(userCredentialRepository.setEmailByUserGUID(5L, "updated@example.com")).thenReturn(1);

        UpdateEmailResponse result = accountService.updateEmailByUserGUID(userGUID, "  updated@example.com  ");

        verify(userCredentialRepository).findByUserGUID(userGUID);
        verify(userCredentialRepository).findByEmail("updated@example.com");
        verify(userCredentialRepository).setEmailByUserGUID(5L, "updated@example.com");
        assertThat(result.userGUID()).isEqualTo(userGUID);
        assertThat(result.email()).isEqualTo("updated@example.com");
    }

    @Test
    void updateEmailByUserGUID_whenEmailUsedByAnotherAccount_throwsConflict() {
        String userGUID = "guid-123";
        UserCredential current = new UserCredential(
                5L, userGUID, "jane@example.com", "Jane", "hash", true, true, false);
        UserCredential other = new UserCredential(
                9L, "guid-999", "updated@example.com", "Other", "hash", true, true, false);
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.of(current));
        when(userCredentialRepository.findByEmail("updated@example.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> accountService.updateEmailByUserGUID(userGUID, "updated@example.com"))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("An account with this email already exists");

        verify(userCredentialRepository, never()).setEmailByUserGUID(anyLong(), anyString());
    }

    @Test
    void updateEmailByUserGUID_whenEmailBelongsToSameAccount_allowsUpdate() throws Exception {
        String userGUID = "guid-123";
        UserCredential current = new UserCredential(
                5L, userGUID, "jane@example.com", "Jane", "hash", true, true, false);
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.of(current));
        when(userCredentialRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(current));
        when(userCredentialRepository.setEmailByUserGUID(5L, "jane@example.com")).thenReturn(1);

        UpdateEmailResponse result = accountService.updateEmailByUserGUID(userGUID, "jane@example.com");

        verify(userCredentialRepository).setEmailByUserGUID(5L, "jane@example.com");
        assertThat(result.email()).isEqualTo("jane@example.com");
    }

    @Test
    void updateEmailByUserGUID_whenNotFound_throws() {
        String userGUID = "missing-guid";
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateEmailByUserGUID(userGUID, "updated@example.com"))
                .isInstanceOf(UserCredentialNotFoundException.class)
                .hasMessage("No account found for userGUID " + userGUID);

        verify(userCredentialRepository).findByUserGUID(userGUID);
        verify(userCredentialRepository, never()).setEmailByUserGUID(anyLong(), anyString());
    }

    @Test
    void addRoleToAccount_whenRoleIsNew_insertsAndReturnsWasAdded() throws Exception {
        String userGUID = "guid-123";
        UserCredential existing = new UserCredential(
                5L, userGUID, "jane@example.com", "Jane", "hash", true, true, false);
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.of(existing));
        when(roleRepository.findRoleNamesByCredentialId(5L)).thenReturn(List.of("member"));

        AccountService.AddRoleServiceResult result = accountService.addRoleToAccount(userGUID, "pastor");

        verify(roleRepository).insert(5L, "pastor");
        assertThat(result.wasAdded()).isTrue();
        assertThat(result.response().userGUID()).isEqualTo(userGUID);
        assertThat(result.response().roles()).containsExactlyInAnyOrder(Role.MEMBER, Role.PASTOR);
    }

    @Test
    void addRoleToAccount_whenRoleAlreadyPresent_skipsInsertAndReturnsNotAdded() throws Exception {
        String userGUID = "guid-123";
        UserCredential existing = new UserCredential(
                5L, userGUID, "jane@example.com", "Jane", "hash", true, true, false);
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.of(existing));
        when(roleRepository.findRoleNamesByCredentialId(5L)).thenReturn(List.of("member"));

        AccountService.AddRoleServiceResult result = accountService.addRoleToAccount(userGUID, "member");

        verify(roleRepository, never()).insert(anyLong(), anyString());
        assertThat(result.wasAdded()).isFalse();
        assertThat(result.response().roles()).containsExactly(Role.MEMBER);
    }

    @Test
    void addRoleToAccount_whenUserNotFound_throws() {
        String userGUID = "missing-guid";
        when(userCredentialRepository.findByUserGUID(userGUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.addRoleToAccount(userGUID, "pastor"))
                .isInstanceOf(UserCredentialNotFoundException.class)
                .hasMessage("No account found for userGUID " + userGUID);

        verify(roleRepository, never()).findRoleNamesByCredentialId(anyLong());
        verify(roleRepository, never()).insert(anyLong(), anyString());
    }
}
