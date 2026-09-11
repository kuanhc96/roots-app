package com.roots.web_client_bff.controller;

import com.roots.web_client_bff.service.SimpleResourceProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleProxyController {
    private final SimpleResourceProxyService simpleResourceProxyService;

    @GetMapping("/pastor")
    public Mono<ResponseEntity<String>> pastor() {
        return simpleResourceProxyService.get("/api/role/pastor");
    }

    @GetMapping("/deacon")
    public Mono<ResponseEntity<String>> deacon() {
        return simpleResourceProxyService.get("/api/role/deacon");
    }

    @GetMapping("/small-group-leader")
    public Mono<ResponseEntity<String>> smallGroupLeader() {
        return simpleResourceProxyService.get("/api/role/small-group-leader");
    }

    @GetMapping("/vice-small-group-leader")
    public Mono<ResponseEntity<String>> viceSmallGroupLeader() {
        return simpleResourceProxyService.get("/api/role/vice-small-group-leader");
    }

    @GetMapping("/member")
    public Mono<ResponseEntity<String>> member() {
        return simpleResourceProxyService.get("/api/role/member");
    }

    @GetMapping("/guest")
    public Mono<ResponseEntity<String>> guest() {
        return simpleResourceProxyService.get("/api/role/guest");
    }
}
