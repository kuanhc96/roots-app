package com.roots.account_management.dto.response;

import java.util.List;

import com.roots.account_management.enums.Role;

public record CreateAccountResponse(
        String name,
        String email,
        String userGUID,
        boolean mfaEnabled,
        boolean emailVerified,
        boolean passwordChangeRequired,
        List<Role> roles
) {}
