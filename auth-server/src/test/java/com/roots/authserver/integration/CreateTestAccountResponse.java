package com.roots.authserver.integration;

import java.util.List;

import com.roots.authserver.enums.Role;

public record CreateTestAccountResponse(
        String name,
        String email,
        String userGUID,
        boolean mfaEnabled,
        boolean emailVerified,
        boolean passwordChangeRequired,
        List<Role> roles
) {}
