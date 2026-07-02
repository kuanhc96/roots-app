package com.roots.authserver.integration;

public record UserCredentialTestingResponse(
        String userGUID,
        String email,
        String name,
        String password,
        boolean mfaEnabled,
        boolean emailVerified,
        boolean passwordChangeRequired
) {}
