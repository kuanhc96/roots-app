package com.roots.account_management_client_bff.controller;

import com.roots.account_management_client_bff.dto.response.LoginStatusResponse;
import com.roots.account_management_client_bff.service.AuthStatusService;
import com.roots.account_management_client_bff.service.LogoutService;
import com.roots.account_management_client_bff.validator.LogoutTokenValidator;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthStatusService authStatusService;
    private final LogoutService logoutService;
    private final LogoutTokenValidator logoutTokenValidator;

    @Operation(
            summary = "Get login status",
            description = "Always 200 — not logged in is a normal answer. The session id is the Spring Session id (the __Host-AMC_SESSION cookie base64 form), which keys the token entries in Redis."
    )
    @GetMapping("/status")
    public LoginStatusResponse getLoginStatus(HttpSession session) {
        return authStatusService.getLoginStatus(session.getId());
    }

    @Operation(
            summary = "Server-side logout",
            description = "Invalidates the browser session and redirects to auth-server's /connect/logout. Tokens are cleared when auth-server sends the back-channel notification."
    )
    @GetMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        URI logoutRedirect = logoutService.buildLogoutRedirect(session.getId());
        session.invalidate();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(logoutRedirect)
                .build();
    }

    @Operation(
            summary = "Receive back-channel logout",
            description = "Validates auth-server's logout_token, records it for replay detection, and clears the tokens associated with its sid."
    )
    @PostMapping("/logout/back-channel/test")
    public ResponseEntity<Void> backChannelLogout(@RequestParam("logout_token") String logoutToken) {
        logoutTokenValidator.validate(logoutToken);
        logoutService.logout(logoutToken);
        return ResponseEntity.ok().build();
    }
}
