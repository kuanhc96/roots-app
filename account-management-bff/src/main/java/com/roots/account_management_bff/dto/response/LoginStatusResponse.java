package com.roots.account_management_bff.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginStatusResponse(
        @JsonProperty("isLoggedIn") boolean isLoggedIn,
        String email,
        String userGUID,
        List<String> roles) {

    public static LoginStatusResponse loggedIn(String email, String userGUID, List<String> roles) {
        return new LoginStatusResponse(true, email, userGUID, roles);
    }

    public static LoginStatusResponse notLoggedIn() {
        return new LoginStatusResponse(false, null, null, null);
    }
}
