package com.roots.account_management.dto.response;

import com.roots.account_management.model.UserCredential;

/**
 * Integration-test-only view of a user's credential: every field including the
 * (hashed) password. The internal surrogate id is deliberately omitted; userGUID is
 * the external identifier. Returned by the protected GET /api/account/test.
 */
public record UserCredentialTestingResponse(
        String userGUID,
        String email,
        String name,
        String password,
        boolean mfaEnabled,
        boolean emailVerified,
        boolean passwordChangeRequired
) {
    public static UserCredentialTestingResponse from(UserCredential credential) {
        return new UserCredentialTestingResponse(
                credential.userGUID(),
                credential.email(),
                credential.name(),
                credential.password(),
                credential.mfaEnabled(),
                credential.emailVerified(),
                credential.passwordChangeRequired()
        );
    }
}
