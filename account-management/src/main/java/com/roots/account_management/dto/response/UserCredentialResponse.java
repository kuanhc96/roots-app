package com.roots.account_management.dto.response;

import com.roots.account_management.model.UserCredential;

/**
 * Restrictive, non-sensitive view of a user's credential: only email, userGUID, and
 * MFA status. No password or verification flags. Returned by the public GET /api/account.
 */
public record UserCredentialResponse(
        String email,
        String userGUID,
        boolean mfaEnabled
) {
    public static UserCredentialResponse from(UserCredential credential) {
        return new UserCredentialResponse(
                credential.email(),
                credential.userGUID(),
                credential.mfaEnabled()
        );
    }
}
