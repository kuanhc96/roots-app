package com.roots.account_management_client_bff.dto.response;

public record AccountProfileResponse(
        String userGUID,
        String email,
        String name,
        Boolean mfaEnabled
) {
}
