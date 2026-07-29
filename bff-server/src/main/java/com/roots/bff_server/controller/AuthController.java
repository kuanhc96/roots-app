package com.roots.bff_server.controller;

import com.roots.bff_server.dto.response.LoginStatusResponse;
import com.roots.bff_server.service.AuthCallbackService;
import com.roots.bff_server.service.AuthStatusService;
import com.roots.bff_server.service.AuthorizeService;
import com.roots.bff_server.service.LogoutService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

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
            description = "Always 200 — \"not logged in\" is a normal answer, not an error. The "
                    + "session id is the Spring Session id (the SESSION cookie is its base64 form), "
                    + "which keys the token entries in Redis."
    )
    @GetMapping("/status")
    public LoginStatusResponse getLoginStatus(HttpSession session) {
        return authStatusService.getLoginStatus(session.getId());
    }

    @Operation(
            summary = "Start the authorization-code flow",
            description = "Kicks off the authorization-code flow: 302 to auth-server's "
                    + "/oauth2/authorize with all parameters filled in, including a freshly minted "
                    + "state held in Redis under this session. Unconditional — an already-authenticated "
                    + "auth-server session just completes the flow silently without showing a login form."
    )
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(HttpSession session) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(authorizeService.buildAuthorizeRedirect(session.getId()))
                .build();
    }

    @Operation(
            summary = "Authorization-code callback",
            description = "Where auth-server sends the browser back with the authorization code "
                    + "(this path is a registered redirect_uri — see AuthCallbackService.CALLBACK_PATH). "
                    + "Validates state, exchanges the code, stores the tokens, and 302s the browser to "
                    + "web-client — \"/\" on success, \"/?e=login_failed\" on any failure."
    )
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            HttpSession session,
            @Parameter(description = "Authorization code issued by auth-server")
            @RequestParam(required = false) String code,
            @Parameter(description = "State value to validate against the one minted at /authorize")
            @RequestParam(required = false) String state,
            @Parameter(description = "Error code from auth-server if authorization failed")
            @RequestParam(required = false) String error) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(authCallbackService.handleCallback(session.getId(), code, state, error))
                .build();
    }

    @Operation(
            summary = "Server-side logout",
            description = "Clears the session's tokens from Redis and 302s the browser to auth-server's "
                    + "/connect/logout for RP-Initiated Logout (with client_id + post_logout_redirect_uri, "
                    + "plus id_token_hint when an id_token is held). Then invalidates the Spring Session so "
                    + "the SESSION cookie and its Redis entry are dropped. auth-server ends its own session "
                    + "and redirects the browser to web-client's /logout."
    )
    @GetMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        // Build the redirect (reads the id_token, clears the tokens) before invalidating
        // the session — invalidate() drops the SESSION cookie and its Redis entry.
        URI logoutRedirect = logoutService.buildLogoutRedirect(session.getId());
        session.invalidate();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(logoutRedirect)
                .build();
    }
}
