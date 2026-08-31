package com.roots.account_management.dto.response;

import com.roots.account_management.model.UserCredential;

public record AccountProfileResponse(
        String userGUID,
        String email,
        String name,
        Boolean mfaEnabled
) {
    public static AccountProfileResponse from(UserCredential credential) {
        return new AccountProfileResponse(
                credential.userGUID(),
                credential.email(),
                credential.name(),
                credential.mfaEnabled()
        );
    }
}