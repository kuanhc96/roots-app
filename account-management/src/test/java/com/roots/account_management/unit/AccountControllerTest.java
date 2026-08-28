package com.roots.account_management.unit;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.account_management.controller.AccountController;
import com.roots.account_management.dto.request.AddRoleRequest;
import com.roots.account_management.dto.request.CreateAccountRequest;
import com.roots.account_management.dto.request.DeleteAccountsRequest;
import com.roots.account_management.dto.request.UpdateEmailRequest;
import com.roots.account_management.dto.request.UpdateMfaRequest;
import com.roots.account_management.dto.request.UpdateNameRequest;
import com.roots.account_management.dto.request.UpdatePasswordRequest;
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
import com.roots.account_management.exception.GlobalExceptionHandler;
import com.roots.account_management.exception.InvalidRequestException;
import com.roots.account_management.service.AccountService;
import com.roots.account_management.validator.Validator;

import com.roots.account_management.service.AccountService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private Validator validator;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AccountController controller = new AccountController(accountService, validator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createTestAccount_withValidRequest_returns201AndBody() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(
                "Jane", "jane@example.com", "Password123", false, true, true, List.of(Role.PASTOR));
        CreateTestAccountResponse response = new CreateTestAccountResponse(
                "Jane", "jane@example.com", "generated-guid", false, true, true, List.of(Role.MEMBER, Role.PASTOR));
        when(accountService.createTestAccount(any())).thenReturn(response);

        mockMvc.perform(post("/api/account/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Jane"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.userGUID").value("generated-guid"))
                .andExpect(jsonPath("$.mfaEnabled").value(false))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.roles[0]").value("member"))
                .andExpect(jsonPath("$.roles[1]").value("pastor"));

        verify(validator).validateCreateAccountRequest(any(CreateAccountRequest.class));
        verify(accountService).createTestAccount(any(CreateAccountRequest.class));
    }

    @Test
    void createTestAccount_whenValidationFails_returns400WithError() throws Exception {
        doThrow(new InvalidRequestException("Name is required"))
                .when(validator).validateCreateAccountRequest(any());

        CreateAccountRequest request = new CreateAccountRequest(
                "", "jane@example.com", "Password123", false, true, false, List.of());

        mockMvc.perform(post("/api/account/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Name is required"));

        verify(accountService, never()).createTestAccount(any());
    }

    @Test
    void createTestAccount_whenEmailExists_returns409WithError() throws Exception {
        when(accountService.createTestAccount(any()))
                .thenThrow(new EmailAlreadyExistsException("An account with this email already exists"));

        CreateAccountRequest request = new CreateAccountRequest(
                "Jane", "jane@example.com", "Password123", false, true, false, List.of());

        mockMvc.perform(post("/api/account/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("An account with this email already exists"));
    }

    @Test
    void deleteTestAccount_byEmail_returns204AndDelegatesToByEmail() throws Exception {
        mockMvc.perform(delete("/api/account/test").param("email", "jane@example.com"))
                .andExpect(status().isNoContent());

        verify(validator).validateAccountLookup("jane@example.com", null);
        verify(accountService).deleteTestAccountByEmail("jane@example.com");
        verify(accountService, never()).deleteTestAccountByUserGUID(anyString());
    }

    @Test
    void deleteTestAccount_byUserGUID_returns204AndDelegatesToByUserGUID() throws Exception {
        mockMvc.perform(delete("/api/account/test").param("userGUID", "some-guid"))
                .andExpect(status().isNoContent());

        verify(validator).validateAccountLookup(null, "some-guid");
        verify(accountService).deleteTestAccountByUserGUID("some-guid");
        verify(accountService, never()).deleteTestAccountByEmail(anyString());
    }

    @Test
    void deleteTestAccount_whenValidationFails_returns400WithError() throws Exception {
        doThrow(new InvalidRequestException("Provide either email or userGUID, not both"))
                .when(validator).validateAccountLookup(anyString(), anyString());

        mockMvc.perform(delete("/api/account/test")
                        .param("email", "jane@example.com")
                        .param("userGUID", "some-guid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Provide either email or userGUID, not both"));

        verify(accountService, never()).deleteTestAccountByEmail(anyString());
        verify(accountService, never()).deleteTestAccountByUserGUID(anyString());
    }

    @Test
    void deleteAccounts_withValidRequest_returns204AndDelegates() throws Exception {
        DeleteAccountsRequest request = new DeleteAccountsRequest(List.of("guid-1", "guid-1", "guid-2"));

        mockMvc.perform(delete("/api/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(validator).validateDeleteAccountsRequest(any(DeleteAccountsRequest.class), anyInt());
        verify(accountService).deleteAccountsByUserGUIDs(request.userGUIDs());
    }

    @Test
    void deleteAccounts_whenValidationFails_returns400WithError() throws Exception {
        doThrow(new InvalidRequestException("userGUIDs must contain at least one value"))
                .when(validator).validateDeleteAccountsRequest(any(DeleteAccountsRequest.class), anyInt());

        mockMvc.perform(delete("/api/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userGUIDs\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("userGUIDs must contain at least one value"));

        verify(accountService, never()).deleteAccountsByUserGUIDs(any());
    }

    @Test
    void getAccountProfiles_withDefaults_returns200AndPagePayload() throws Exception {
        AccountProfilesResponse response = new AccountProfilesResponse(
                0,
                20,
                1,
                List.of(new AccountProfileResponse("guid-1", "jane@example.com", "Jane"))
        );
        when(accountService.getAccountProfiles(0, 20)).thenReturn(response);

        mockMvc.perform(get("/api/account/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.accounts[0].userGUID").value("guid-1"))
                .andExpect(jsonPath("$.accounts[0].email").value("jane@example.com"))
                .andExpect(jsonPath("$.accounts[0].name").value("Jane"));

        verify(validator).validatePagination(0, 20, 100);
        verify(accountService).getAccountProfiles(0, 20);
    }

    @Test
    void getAccountProfiles_withCustomPageAndSize_delegatesToService() throws Exception {
        AccountProfilesResponse response = new AccountProfilesResponse(2, 10, 30, List.of());
        when(accountService.getAccountProfiles(2, 10)).thenReturn(response);

        mockMvc.perform(get("/api/account/profiles")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));

        verify(validator).validatePagination(2, 10, 100);
        verify(accountService).getAccountProfiles(2, 10);
    }

    @Test
    void getAccountProfiles_whenValidationFails_returns400WithError() throws Exception {
        doThrow(new InvalidRequestException("size must be less than or equal to 100"))
                .when(validator).validatePagination(anyInt(), anyInt(), anyInt());

        mockMvc.perform(get("/api/account/profiles").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("size must be less than or equal to 100"));

        verify(accountService, never()).getAccountProfiles(anyInt(), anyInt());
    }

    @Test
    void searchAccountProfiles_byEmail_usesDefaultParams() throws Exception {
        when(accountService.searchAccountProfiles("jane@example.com", null, false, 100))
                .thenReturn(List.of(new AccountProfileResponse("guid-1", "jane@example.com", "Jane")));

        mockMvc.perform(post("/api/account/search").param("email", "jane@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userGUID").value("guid-1"))
                .andExpect(jsonPath("$[0].email").value("jane@example.com"))
                .andExpect(jsonPath("$[0].name").value("Jane"));

        verify(validator).validateSearchInput("jane@example.com", null, 100);
        verify(accountService).searchAccountProfiles("jane@example.com", null, false, 100);
    }

    @Test
    void searchAccountProfiles_byName_withFullMatchAndMaxCount_delegatesToService() throws Exception {
        when(accountService.searchAccountProfiles(null, "Jane", true, 5)).thenReturn(List.of());

        mockMvc.perform(post("/api/account/search")
                        .param("name", "Jane")
                        .param("fullMatch", "true")
                        .param("maxCount", "5"))
                .andExpect(status().isOk());

        verify(validator).validateSearchInput(null, "Jane", 5);
        verify(accountService).searchAccountProfiles(null, "Jane", true, 5);
    }

    @Test
    void searchAccountProfiles_whenValidationFails_returns400WithError() throws Exception {
        doThrow(new InvalidRequestException("Provide either email or name, not both"))
                .when(validator).validateSearchInput(anyString(), anyString(), anyInt());

        mockMvc.perform(post("/api/account/search")
                        .param("email", "a@example.com")
                        .param("name", "Jane"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Provide either email or name, not both"));

        verify(accountService, never()).searchAccountProfiles(anyString(), anyString(), anyBoolean(), anyInt());
    }

    @Test
    void updateMfaEnabled_withValidRequest_returns200AndBody() throws Exception {
        String userGUID = "test-guid";
        UpdateMfaRequest request = new UpdateMfaRequest(false);
        when(accountService.updateMfaEnabledByUserGUID(userGUID, false))
                .thenReturn(UpdateMfaResponse.builder().userGUID(userGUID).mfaEnabled(false).build());

        mockMvc.perform(put("/api/account/mfa/{userGUID}", userGUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userGUID").value(userGUID))
                .andExpect(jsonPath("$.mfaEnabled").value(false));

        verify(validator).validateUserGUID(userGUID);
        verify(validator).validateUpdateMfaRequest(any(UpdateMfaRequest.class));
        verify(accountService).updateMfaEnabledByUserGUID(userGUID, false);
    }

    @Test
    void updateMfaEnabled_whenRequestValidationFails_returns400WithError() throws Exception {
        String userGUID = "test-guid";
        doThrow(new InvalidRequestException("mfaEnabled is required"))
                .when(validator).validateUpdateMfaRequest(any(UpdateMfaRequest.class));

        mockMvc.perform(put("/api/account/mfa/{userGUID}", userGUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateMfaRequest(null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("mfaEnabled is required"));

        verify(accountService, never()).updateMfaEnabledByUserGUID(anyString(), anyBoolean());
    }

    @Test
    void updatePassword_withValidRequest_returns200AndBody() throws Exception {
        String userGUID = "test-guid";
        UpdatePasswordRequest request = new UpdatePasswordRequest("NewPassword123");
        when(accountService.updatePasswordByUserGUID(userGUID, "NewPassword123"))
                .thenReturn(UpdatePasswordResponse.builder().userGUID(userGUID).build());

        mockMvc.perform(put("/api/account/password/{userGUID}", userGUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userGUID").value(userGUID));

        verify(validator).validateUserGUID(userGUID);
        verify(validator).validateUpdatePasswordRequest(any(UpdatePasswordRequest.class));
        verify(accountService).updatePasswordByUserGUID(userGUID, "NewPassword123");
    }

    @Test
    void updatePassword_whenRequestValidationFails_returns400WithError() throws Exception {
        String userGUID = "test-guid";
        doThrow(new InvalidRequestException("Password must be at least 8 characters"))
                .when(validator).validateUpdatePasswordRequest(any(UpdatePasswordRequest.class));

        mockMvc.perform(put("/api/account/password/{userGUID}", userGUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePasswordRequest("short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password must be at least 8 characters"));

        verify(accountService, never()).updatePasswordByUserGUID(anyString(), anyString());
    }

    @Test
    void updateName_withValidRequest_returns200AndBody() throws Exception {
        String userGUID = "test-guid";
        UpdateNameRequest request = new UpdateNameRequest("  Jane Doe  ");
        when(accountService.updateNameByUserGUID(userGUID, "  Jane Doe  "))
                .thenReturn(UpdateNameResponse.builder().userGUID(userGUID).name("Jane Doe").build());

        mockMvc.perform(put("/api/account/name/{userGUID}", userGUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userGUID").value(userGUID))
                .andExpect(jsonPath("$.name").value("Jane Doe"));

        verify(validator).validateUserGUID(userGUID);
        verify(validator).validateUpdateNameRequest(any(UpdateNameRequest.class));
        verify(accountService).updateNameByUserGUID(userGUID, "  Jane Doe  ");
    }

    @Test
    void updateName_whenRequestValidationFails_returns400WithError() throws Exception {
        String userGUID = "test-guid";
        doThrow(new InvalidRequestException("Name is required"))
                .when(validator).validateUpdateNameRequest(any(UpdateNameRequest.class));

        mockMvc.perform(put("/api/account/name/{userGUID}", userGUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateNameRequest("  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Name is required"));

        verify(accountService, never()).updateNameByUserGUID(anyString(), anyString());
    }

    @Test
    void updateEmail_withValidRequest_returns200AndBody() throws Exception {
        String userGUID = "test-guid";
        UpdateEmailRequest request = new UpdateEmailRequest("  updated@example.com  ");
        when(accountService.updateEmailByUserGUID(userGUID, "  updated@example.com  "))
                .thenReturn(UpdateEmailResponse.builder().userGUID(userGUID).email("updated@example.com").build());

        mockMvc.perform(put("/api/account/email/{userGUID}", userGUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userGUID").value(userGUID))
                .andExpect(jsonPath("$.email").value("updated@example.com"));

        verify(validator).validateUserGUID(userGUID);
        verify(validator).validateUpdateEmailRequest(any(UpdateEmailRequest.class));
        verify(accountService).updateEmailByUserGUID(userGUID, "  updated@example.com  ");
    }

    @Test
    void updateEmail_whenRequestValidationFails_returns400WithError() throws Exception {
        String userGUID = "test-guid";
        doThrow(new InvalidRequestException("Email must contain an \"@\""))
                .when(validator).validateUpdateEmailRequest(any(UpdateEmailRequest.class));

        mockMvc.perform(put("/api/account/email/{userGUID}", userGUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateEmailRequest("invalid-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email must contain an \"@\""));

        verify(accountService, never()).updateEmailByUserGUID(anyString(), anyString());
    }

    @Test
    void addRole_whenRoleIsNew_returns201AndBody() throws Exception {
        String userGUID = "test-guid";
        AddRoleRequest request = new AddRoleRequest("pastor");
        AddRoleResponse serviceResponse = new AddRoleResponse(userGUID, List.of(Role.MEMBER, Role.PASTOR));
        when(accountService.addRoleToAccount(userGUID, "pastor"))
                .thenReturn(new AccountService.AddRoleServiceResult(true, serviceResponse));

        mockMvc.perform(post("/api/account/role/{userGUID}", userGUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userGUID").value(userGUID))
                .andExpect(jsonPath("$.roles[0]").value("member"))
                .andExpect(jsonPath("$.roles[1]").value("pastor"));

        verify(validator).validateUserGUID(userGUID);
        verify(validator).validateAddRoleRequest(any(AddRoleRequest.class));
        verify(accountService).addRoleToAccount(userGUID, "pastor");
    }

    @Test
    void addRole_whenRoleAlreadyPresent_returns200AndBody() throws Exception {
        String userGUID = "test-guid";
        AddRoleRequest request = new AddRoleRequest("member");
        AddRoleResponse serviceResponse = new AddRoleResponse(userGUID, List.of(Role.MEMBER));
        when(accountService.addRoleToAccount(userGUID, "member"))
                .thenReturn(new AccountService.AddRoleServiceResult(false, serviceResponse));

        mockMvc.perform(post("/api/account/role/{userGUID}", userGUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userGUID").value(userGUID))
                .andExpect(jsonPath("$.roles[0]").value("member"));
    }

    @Test
    void addRole_whenValidationFails_returns400WithError() throws Exception {
        String userGUID = "test-guid";
        doThrow(new InvalidRequestException("GUEST role cannot be added"))
                .when(validator).validateAddRoleRequest(any(AddRoleRequest.class));

        mockMvc.perform(post("/api/account/role/{userGUID}", userGUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddRoleRequest("guest"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GUEST role cannot be added"));

        verify(accountService, never()).addRoleToAccount(anyString(), anyString());
    }
}
