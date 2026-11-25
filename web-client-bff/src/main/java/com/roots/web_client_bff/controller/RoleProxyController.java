package com.roots.web_client_bff.controller;

import com.roots.web_client_bff.service.RoleProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleProxyController {
    private final RoleProxyService roleProxyService;

    @GetMapping("/{role:pastor|deacon|small-group-leader|vice-small-group-leader|member|guest}")
    public ResponseEntity<String> getRole(@PathVariable String role, Authentication authentication,
                                          HttpServletRequest request, HttpServletResponse response) {
        return roleProxyService.forward(authentication, request, response, role);
    }
}
