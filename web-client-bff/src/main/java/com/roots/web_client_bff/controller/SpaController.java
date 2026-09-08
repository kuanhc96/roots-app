package com.roots.web_client_bff.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class SpaController {
    @GetMapping(value = {"/", "/home", "/logout", "/session-expired"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public Mono<ResponseEntity<Resource>> spaShell() {
        Resource indexHtml = new ClassPathResource("static/index.html");
        return Mono.just(ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(indexHtml));
    }
}
