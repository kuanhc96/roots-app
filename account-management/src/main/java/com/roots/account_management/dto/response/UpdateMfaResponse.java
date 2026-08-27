package com.roots.account_management.dto.response;

import lombok.Builder;

@Builder
public record UpdateMfaResponse(String userGUID, Boolean mfaEnabled) {}
