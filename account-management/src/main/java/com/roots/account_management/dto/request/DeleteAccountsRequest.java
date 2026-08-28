package com.roots.account_management.dto.request;

import java.util.List;

public record DeleteAccountsRequest(List<String> userGUIDs) {}
