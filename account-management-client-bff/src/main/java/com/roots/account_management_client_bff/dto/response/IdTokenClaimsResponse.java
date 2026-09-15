package com.roots.account_management_client_bff.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdTokenClaimsResponse(
        String email,
        String userGUID,
        List<String> roles) {
}
