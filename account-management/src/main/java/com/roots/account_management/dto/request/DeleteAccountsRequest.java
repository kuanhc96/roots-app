package com.roots.account_management.dto.request;

import java.util.List;

import lombok.Builder;

@Builder
public record DeleteAccountsRequest(List<String> userGUIDs) {}
