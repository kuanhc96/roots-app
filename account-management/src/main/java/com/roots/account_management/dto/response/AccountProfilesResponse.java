package com.roots.account_management.dto.response;

import java.util.List;

public record AccountProfilesResponse(
        int page,
        int size,
        long totalElements,
        List<AccountProfileResponse> accounts
) {}
