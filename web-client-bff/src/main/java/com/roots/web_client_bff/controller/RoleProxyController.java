package com.roots.web_client_bff.controller;

import com.roots.web_client_bff.service.RoleProxyService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * The SPA's entry point to simple-resource-server's role endpoints. The browser sends
 * only its session cookie; the bff looks up (or refreshes) the session's access token
 * and forwards the call through gateway-server with it as a bearer token. Downstream
 * 2xx/4xx answers are relayed verbatim (e.g. 403 for a missing role); 401 when the
 * session holds no usable token; 502 when the downstream fails.
 */
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleProxyController {

    private final RoleProxyService roleProxyService;

    @Operation(summary = "Proxy to simple-resource-server /api/role/pastor (requires ROLE_PASTOR)")
    @GetMapping("/pastor")
    public ResponseEntity<String> getPastor(HttpSession session) {
        return roleProxyService.forward(session.getId(), "pastor");
    }

    @Operation(summary = "Proxy to simple-resource-server /api/role/deacon (requires ROLE_DEACON)")
    @GetMapping("/deacon")
    public ResponseEntity<String> getDeacon(HttpSession session) {
        return roleProxyService.forward(session.getId(), "deacon");
    }

    @Operation(summary = "Proxy to simple-resource-server /api/role/small-group-leader (requires ROLE_SMALL_GROUP_LEADER)")
    @GetMapping("/small-group-leader")
    public ResponseEntity<String> getSmallGroupLeader(HttpSession session) {
        return roleProxyService.forward(session.getId(), "small-group-leader");
    }

    @Operation(summary = "Proxy to simple-resource-server /api/role/vice-small-group-leader (requires ROLE_VICE_SMALL_GROUP_LEADER)")
    @GetMapping("/vice-small-group-leader")
    public ResponseEntity<String> getViceSmallGroupLeader(HttpSession session) {
        return roleProxyService.forward(session.getId(), "vice-small-group-leader");
    }

    @Operation(summary = "Proxy to simple-resource-server /api/role/member (requires ROLE_MEMBER)")
    @GetMapping("/member")
    public ResponseEntity<String> getMember(HttpSession session) {
        return roleProxyService.forward(session.getId(), "member");
    }

    @Operation(summary = "Proxy to simple-resource-server /api/role/guest (requires WEB_CLIENT_READ scope only)")
    @GetMapping("/guest")
    public ResponseEntity<String> getGuest(HttpSession session) {
        return roleProxyService.forward(session.getId(), "guest");
    }
}
