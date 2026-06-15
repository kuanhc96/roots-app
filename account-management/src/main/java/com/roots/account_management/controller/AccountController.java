package com.roots.account_management.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roots.account_management.dto.request.CreateAccountRequest;
import com.roots.account_management.dto.response.CreateAccountResponse;
import com.roots.account_management.service.AccountService;
import com.roots.account_management.validator.Validator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final Validator validator;

    @Operation(
            summary = "Create a test account",
            description = "Integration-test-only endpoint: lets the INTEGRATION_TEST_CLIENT "
                    + "(client_credentials) create an account with arbitrary mfa/emailVerified/roles."
    )
    @PostMapping("/test")
    @PreAuthorize("hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAccountResponse createTestAccount(@RequestBody CreateAccountRequest createAccountRequest) {
        validator.validateCreateAccountRequest(createAccountRequest);
        return accountService.createTestAccount(createAccountRequest);
    }

    @Operation(
            summary = "Delete a test account",
            description = "Integration-test-only endpoint: lets the INTEGRATION_TEST_CLIENT "
                    + "(client_credentials) delete an account by exactly one of email or userGUID."
    )
    @DeleteMapping("/test")
    @PreAuthorize("hasAuthority('INTEGRATION_TEST_CLIENT_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTestAccount(
            @Parameter(description = "Email of the account to delete; provide this or userGUID, not both")
            @RequestParam(required = false) String email,
            @Parameter(description = "GUID of the account to delete; provide this or email, not both")
            @RequestParam(required = false) String userGUID) {
        validator.validateDeleteAccountRequest(email, userGUID);
        if (StringUtils.isNotBlank(email)) {
            accountService.deleteTestAccountByEmail(email);
        } else {
            accountService.deleteTestAccountByUserGUID(userGUID);
        }
    }
}
