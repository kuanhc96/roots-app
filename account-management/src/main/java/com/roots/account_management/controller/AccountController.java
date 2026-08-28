package com.roots.account_management.controller;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roots.account_management.dto.request.CreateAccountRequest;
import com.roots.account_management.dto.request.DeleteAccountsRequest;
import com.roots.account_management.dto.request.UpdateEmailRequest;
import com.roots.account_management.dto.request.UpdateMfaRequest;
import com.roots.account_management.dto.request.UpdateNameRequest;
import com.roots.account_management.dto.request.UpdatePasswordRequest;
import com.roots.account_management.dto.response.AccountProfileResponse;
import com.roots.account_management.dto.response.AccountProfilesResponse;
import com.roots.account_management.dto.response.CreateTestAccountResponse;
import com.roots.account_management.dto.response.UpdateEmailResponse;
import com.roots.account_management.dto.response.UpdateMfaResponse;
import com.roots.account_management.dto.response.UpdateNameResponse;
import com.roots.account_management.dto.response.UpdatePasswordResponse;
import com.roots.account_management.dto.response.UserCredentialResponse;
import com.roots.account_management.dto.response.UserCredentialTestingResponse;
import com.roots.account_management.exception.UserCredentialNotFoundException;
import com.roots.account_management.model.UserCredential;
import com.roots.account_management.service.AccountService;
import com.roots.account_management.validator.Validator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int DEFAULT_SEARCH_MAX_COUNT = 100;
    private static final int MAX_DELETE_ACCOUNT_COUNT = 10;

    private final AccountService accountService;
    private final Validator validator;

    @Operation(
            summary = "Create a test account",
            description = "Integration-test-only endpoint: lets the INTEGRATION_TEST_CLIENT "
                    + "(client_credentials) create an account with arbitrary mfa/emailVerified/passwordChangeRequired/roles."
    )
    @PostMapping("/test")
    @PreAuthorize("hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTestAccountResponse createTestAccount(@RequestBody CreateAccountRequest createAccountRequest) {
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
        validator.validateAccountLookup(email, userGUID);
        if (StringUtils.isNotBlank(email)) {
            accountService.deleteTestAccountByEmail(email);
        } else {
            accountService.deleteTestAccountByUserGUID(userGUID);
        }
    }

    @Operation(
            summary = "Delete accounts",
            description = "Public endpoint: deletes accounts by a list of userGUID values. Missing userGUIDs are treated as already deleted."
    )
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccounts(@RequestBody DeleteAccountsRequest deleteAccountsRequest) {
        validator.validateDeleteAccountsRequest(deleteAccountsRequest, MAX_DELETE_ACCOUNT_COUNT);
        accountService.deleteAccountsByUserGUIDs(deleteAccountsRequest.userGUIDs());
    }

    @Operation(
            summary = "Get a test account (all fields)",
            description = "Integration-test-only endpoint: lets the INTEGRATION_TEST_CLIENT "
                    + "(client_credentials) read every field of an account, including the hashed "
                    + "password, by exactly one of email or userGUID."
    )
    @GetMapping("/test")
    @PreAuthorize("hasAuthority('INTEGRATION_TEST_CLIENT_READ')")
    public UserCredentialTestingResponse getTestAccount(
            @Parameter(description = "Email of the account to fetch; provide this or userGUID, not both")
            @RequestParam(required = false) String email,
            @Parameter(description = "GUID of the account to fetch; provide this or email, not both")
            @RequestParam(required = false) String userGUID) throws UserCredentialNotFoundException {
        return UserCredentialTestingResponse.from(lookup(email, userGUID));
    }

    @Operation(
            summary = "Get an account (restricted fields)",
            description = "Public endpoint: returns only email, userGUID, and MFA status for an "
                    + "account, by exactly one of email or userGUID."
    )
    @GetMapping
    public UserCredentialResponse getAccount(
            @Parameter(description = "Email of the account to fetch; provide this or userGUID, not both")
            @RequestParam(required = false) String email,
            @Parameter(description = "GUID of the account to fetch; provide this or email, not both")
            @RequestParam(required = false) String userGUID) throws UserCredentialNotFoundException {
        return UserCredentialResponse.from(lookup(email, userGUID));
    }

    @Operation(
            summary = "Get account profile",
            description = "Public endpoint: returns userGUID, email, and name for an account, by exactly "
                    + "one of email or userGUID."
    )
    @GetMapping("/profile")
    public AccountProfileResponse getAccountProfile(
            @Parameter(description = "Email of the account to fetch; provide this or userGUID, not both")
            @RequestParam(required = false) String email,
            @Parameter(description = "GUID of the account to fetch; provide this or email, not both")
            @RequestParam(required = false) String userGUID) throws UserCredentialNotFoundException {
        return AccountProfileResponse.from(lookup(email, userGUID));
    }

    @Operation(
            summary = "Get account profiles",
            description = "Public endpoint: returns paginated account profile rows (userGUID, email, name)."
    )
    @GetMapping("/profiles")
    public AccountProfilesResponse getAccountProfiles(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;

        validator.validatePagination(resolvedPage, resolvedSize, MAX_SIZE);
        return accountService.getAccountProfiles(resolvedPage, resolvedSize);
    }

    @Operation(
            summary = "Search account profiles",
            description = "Public endpoint: searches accounts by either email or name (not both). "
                    + "When fullMatch is true, performs exact case-insensitive matching; otherwise fuzzy case-insensitive contains matching."
    )
    @PostMapping("/search")
    public List<AccountProfileResponse> searchAccountProfiles(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") boolean fullMatch,
            @RequestParam(defaultValue = "" + DEFAULT_SEARCH_MAX_COUNT) int maxCount) {

        validator.validateSearchInput(email, name, maxCount);
        return accountService.searchAccountProfiles(email, name, fullMatch, maxCount);
    }

    @Operation(
            summary = "Update MFA enabled status",
            description = "Public endpoint: updates is_mfa_enabled by userGUID and returns the updated "
                    + "restricted account view."
    )
    @PutMapping("/mfa/{userGUID}")
    public UpdateMfaResponse updateMfaEnabled(
            @Parameter(description = "GUID of the account to update")
            @PathVariable String userGUID,
            @RequestBody UpdateMfaRequest updateMfaRequest) throws UserCredentialNotFoundException {
        validator.validateUserGUID(userGUID);
        validator.validateUpdateMfaRequest(updateMfaRequest);
        return accountService.updateMfaEnabledByUserGUID(userGUID, updateMfaRequest.mfaEnabled());
    }

    @Operation(
            summary = "Update account password",
            description = "Public endpoint: updates password by userGUID using auth-server password policy."
    )
    @PutMapping("/password/{userGUID}")
    public UpdatePasswordResponse updatePassword(
            @Parameter(description = "GUID of the account to update")
            @PathVariable String userGUID,
            @RequestBody UpdatePasswordRequest updatePasswordRequest) throws UserCredentialNotFoundException {
        validator.validateUserGUID(userGUID);
        validator.validateUpdatePasswordRequest(updatePasswordRequest);
        return accountService.updatePasswordByUserGUID(userGUID, updatePasswordRequest.password());
    }

    @Operation(
            summary = "Update account name",
            description = "Public endpoint: updates name by userGUID. Name is trimmed and must be non-blank."
    )
    @PutMapping("/name/{userGUID}")
    public UpdateNameResponse updateName(
            @Parameter(description = "GUID of the account to update")
            @PathVariable String userGUID,
            @RequestBody UpdateNameRequest updateNameRequest) throws UserCredentialNotFoundException {
        validator.validateUserGUID(userGUID);
        validator.validateUpdateNameRequest(updateNameRequest);
        return accountService.updateNameByUserGUID(userGUID, updateNameRequest.name());
    }

    @Operation(
            summary = "Update account email",
            description = "Public endpoint: updates email by userGUID. Email is trimmed and validated."
    )
    @PutMapping("/email/{userGUID}")
    public UpdateEmailResponse updateEmail(
            @Parameter(description = "GUID of the account to update")
            @PathVariable String userGUID,
            @RequestBody UpdateEmailRequest updateEmailRequest) throws UserCredentialNotFoundException {
        validator.validateUserGUID(userGUID);
        validator.validateUpdateEmailRequest(updateEmailRequest);
        return accountService.updateEmailByUserGUID(userGUID, updateEmailRequest.email());
    }

    private UserCredential lookup(String email, String userGUID) throws UserCredentialNotFoundException {
        validator.validateAccountLookup(email, userGUID);
        return StringUtils.isNotBlank(email)
                ? accountService.getUserCredentialByEmail(email)
                : accountService.getUserCredentialByUserGUID(userGUID);
    }
}
