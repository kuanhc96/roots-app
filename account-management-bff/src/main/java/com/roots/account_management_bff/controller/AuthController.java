package com.roots.account_management_bff.controller;

import com.roots.account_management_bff.dto.response.LoginStatusResponse;
import com.roots.account_management_bff.service.AuthCallbackService;
import com.roots.account_management_bff.service.AuthStatusService;
import com.roots.account_management_bff.service.AuthorizeService;
import com.roots.account_management_bff.service.LogoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthStatusService authStatusService;
    private final AuthorizeService authorizeService;
    private final AuthCallbackService authCallbackService;
    private final LogoutService logoutService;

    @Operation(
            summary = "Get login status",
            description = "Always 200 — not logged in is a normal answer. The session id is the Spring Session id (the __Host-AMC_SESSION cookie base64 form), which keys the token entries in Redis."
    )
    @GetMapping("/status")
    public LoginStatusResponse getLoginStatus(HttpSession session) {
        return authStatusService.getLoginStatus(session.getId());
    }

    @Operation(
            summary = "Start the authorization-code flow",
            description = "Kicks off the authorization-code flow: 302 to auth-server's /oauth2/authorize with parameters filled in, including a minted state held in Redis under this session."
    )
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(HttpSession session) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(authorizeService.buildAuthorizeRedirect(session.getId()))
                .build();
    }

    @Operation(
            summary = "Authorization-code callback",
            description = "Auth-server redirects here with the authorization code. Validates state, exchanges the code, stores the tokens, and 302s the browser to the account-management client home page."
    )
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            HttpSession session,
            @Parameter(description = "Authorization code")
            @RequestParam(required = false) String code,
            @Parameter(description = "State value")
            @RequestParam(required = false) String state,
            @Parameter(description = "Error code from auth-server")
            @RequestParam(required = false) String error) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(authCallbackService.handleCallback(session.getId(), code, state, error))
                .build();
    }

    @Operation(
            summary = "Server-side logout",
            description = "Clears the token keys, invalidates the session, and redirects the browser to auth-server's /connect/logout."
    )
    @GetMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        URI logoutRedirect = logoutService.buildLogoutRedirect(session.getId());
        session.invalidate();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(logoutRedirect)
                .build();
    }
}
