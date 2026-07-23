package com.roots.bff_server.controller;

import com.roots.bff_server.dto.response.LoginStatusResponse;
import com.roots.bff_server.service.AuthStatusService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthStatusService authStatusService;

    /**
     * Always 200 — "not logged in" is a normal answer, not an error. The session id is
     * the Spring Session id (the SESSION cookie is its base64 form), which keys the
     * token entries in Redis.
     */
    @GetMapping("/status")
    public LoginStatusResponse getLoginStatus(HttpSession session) {
        return authStatusService.getLoginStatus(session.getId());
    }
}
