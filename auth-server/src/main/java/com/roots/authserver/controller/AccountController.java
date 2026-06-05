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
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final UserCredentialService userCredentialService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAccountResponse createAccount(@RequestBody CreateAccountRequest createAccountRequest) {
        return userCredentialService.createAccount(createAccountRequest);
    }
}
