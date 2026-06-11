package com.roots.authserver.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "New-account registration payload")
public record CreateAccountRequest(
        @Schema(description = "Display name (max 255 chars)", example = "Jane Doe")
        String name,
        @Schema(description = "Email address; used as the login username", example = "jane@example.com")
        String email,
        @Schema(description = "Password: min 8 chars with at least one uppercase, lowercase, and digit", example = "Passw0rd")
        String password) {}
