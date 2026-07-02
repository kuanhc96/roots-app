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
import com.roots.account_management.dto.request.CreateAccountRequest;
import com.roots.account_management.dto.response.CreateTestAccountResponse;
import com.roots.account_management.enums.Role;
import com.roots.account_management.exception.EmailAlreadyExistsException;
import com.roots.account_management.exception.GlobalExceptionHandler;
import com.roots.account_management.exception.InvalidRequestException;
import com.roots.account_management.service.AccountService;
import com.roots.account_management.validator.Validator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit test for {@link AccountController} using standalone MockMvc: the controller is
 * exercised in isolation with the service and validator mocked, and the real
 * {@link GlobalExceptionHandler} wired in so exception-to-status mapping is covered.
 * No Spring context or security filter chain is loaded — @PreAuthorize is not enforced
 * here (that is covered by the integration tests' bearer-token flows).
 */
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
}
