package com.roots.account_management_client_bff.controller;

import com.roots.account_management_client_bff.dto.response.LoginStatusResponse;
import com.roots.account_management_client_bff.service.AuthStatusService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthStatusService authStatusService;

    @Operation(summary = "Get login status",
            description = "Always 200. Returns identity claims from the authenticated OIDC session, without exchanging tokens.")
    @GetMapping("/status")
    public LoginStatusResponse getLoginStatus(Authentication authentication) {
        return authStatusService.getLoginStatus(authentication);
    }
}
