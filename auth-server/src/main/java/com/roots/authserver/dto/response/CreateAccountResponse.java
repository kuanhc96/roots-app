package com.roots.authserver.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Created-account summary")
public record CreateAccountResponse(
        @Schema(description = "Display name of the created account", example = "Jane Doe")
        String name,
        @Schema(description = "Email of the created account", example = "jane@example.com")
        String email) {}
