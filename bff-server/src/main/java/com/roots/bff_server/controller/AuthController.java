package com.roots.bff_server.controller;

import com.roots.bff_server.dto.response.LoginStatusResponse;
import com.roots.bff_server.service.AuthCallbackService;
import com.roots.bff_server.service.AuthStatusService;
import com.roots.bff_server.service.AuthorizeService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthStatusService authStatusService;
    private final AuthorizeService authorizeService;
    private final AuthCallbackService authCallbackService;

    /**
     * Always 200 — "not logged in" is a normal answer, not an error. The session id is
     * the Spring Session id (the SESSION cookie is its base64 form), which keys the
     * token entries in Redis.
     */
    @GetMapping("/status")
    public LoginStatusResponse getLoginStatus(HttpSession session) {
        return authStatusService.getLoginStatus(session.getId());
    }

    /**
     * Kicks off the authorization-code flow: 302 to auth-server's /oauth2/authorize
     * with all parameters filled in, including a freshly minted {@code state} held in
     * Redis under this session. Unconditional — an already-authenticated auth-server
     * session just completes the flow silently without showing a login form.
     */
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(HttpSession session) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(authorizeService.buildAuthorizeRedirect(session.getId()))
                .build();
    }

    /**
     * Where auth-server sends the browser back with the authorization code (this
     * path is a registered redirect_uri — see {@link AuthCallbackService#CALLBACK_PATH}).
     * Validates state, exchanges the code, stores the tokens, and 302s the browser
     * to web-client — "/" on success, "/?e=login_failed" on any failure.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            HttpSession session,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(authCallbackService.handleCallback(session.getId(), code, state, error))
                .build();
    }
}
