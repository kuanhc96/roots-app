package com.roots.authserver.dto.request;

import lombok.Builder;

@Builder
public record CreateAccountRequest(String name, String email, String password) {}
