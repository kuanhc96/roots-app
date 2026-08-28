package com.roots.account_management.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.roots.account_management.controller.AccountController;
import com.roots.account_management.exception.GlobalExceptionHandler;
import com.roots.account_management.exception.InvalidRequestException;
import com.roots.account_management.model.UserCredential;
import com.roots.account_management.service.AccountService;
import com.roots.account_management.validator.Validator;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerProfileTest {

    @Mock
    private AccountService accountService;

    @Mock
    private Validator validator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AccountController controller = new AccountController(accountService, validator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAccountProfile_byEmail_returnsUserGUIDEmailAndName() throws Exception {
        String email = "jane@example.com";
        when(accountService.getUserCredentialByEmail(email)).thenReturn(new UserCredential(
                7L,
                "guid-123",
                email,
                "Jane",
                "hash",
                true,
                true,
                false
        ));

        mockMvc.perform(get("/api/account/profile").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userGUID").value("guid-123"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Jane"));

        verify(validator).validateAccountLookup(email, null);
        verify(accountService).getUserCredentialByEmail(email);
    }

    @Test
    void getAccountProfile_whenValidationFails_returns400() throws Exception {
        doThrow(new InvalidRequestException("Provide either email or userGUID"))
                .when(validator).validateAccountLookup(null, null);

        mockMvc.perform(get("/api/account/profile"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Provide either email or userGUID"));
    }
}
