package com.roots.authserver.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roots.authserver.dto.request.CreateAccountRequest;
import com.roots.authserver.dto.response.CreateAccountResponse;
import com.roots.authserver.service.UserCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Account creation", description = "Public self-registration endpoint")
public class AccountController {

    private final UserCredentialService userCredentialService;

    @Operation(
            summary = "Create a new user account",
            description = "Public self-registration. Validates the name/email/password server-side, hashes the "
                    + "password (bcrypt), persists the credential with a default 'member' role, and returns the "
                    + "created account. The new account starts with MFA enabled and email unverified; the signup "
                    + "page then auto-starts the login flow to drive magic-link email verification.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Invalid name, email, or password"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAccountResponse createAccount(@RequestBody CreateAccountRequest createAccountRequest) {
        return userCredentialService.createAccount(createAccountRequest);
    }
}
