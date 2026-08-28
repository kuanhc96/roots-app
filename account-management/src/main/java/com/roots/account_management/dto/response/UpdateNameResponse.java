package com.roots.account_management.dto.response;

import lombok.Builder;

@Builder
public record UpdateNameResponse(String userGUID, String name) {}
