package com.roots.account_management.dto.request;

import java.util.List;

import com.roots.account_management.enums.Role;

public record CreateAccountRequest(
        String name,
        String email,
        String password,
        Boolean mfaEnabled,
        Boolean emailVerified,
        List<Role> roles
) {
    private static final boolean DEFAULT_MFA_ENABLED = true;
    private static final boolean DEFAULT_EMAIL_VERIFIED = false;

    // Resolve the optional flags at construction (Jackson uses the canonical
    // constructor when deserializing), so an omitted/null value becomes its
    // default and the accessors never return null.
    public CreateAccountRequest {
        mfaEnabled = mfaEnabled == null ? DEFAULT_MFA_ENABLED : mfaEnabled;
        emailVerified = emailVerified == null ? DEFAULT_EMAIL_VERIFIED : emailVerified;
    }
}
