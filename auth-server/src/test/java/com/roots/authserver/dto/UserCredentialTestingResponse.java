package com.roots.authserver.dto;

public record UserCredentialTestingResponse(
        String userGUID,
        String email,
        String name,
        String password,
        boolean mfaEnabled,
        boolean emailVerified,
        boolean passwordChangeRequired
) {}
