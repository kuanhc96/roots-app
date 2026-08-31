package com.roots.account_management.dto.request;

import lombok.Builder;

@Builder
public record AddRoleRequest(String role) {
}
