package com.roots.authserver.dto.request;

public record CreateAccountRequest(String name, String email, String password) {}
