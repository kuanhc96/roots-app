package com.roots.account_management.dto.request;

import lombok.Builder;

@Builder
public record UpdateMfaRequest(Boolean mfaEnabled) {}
