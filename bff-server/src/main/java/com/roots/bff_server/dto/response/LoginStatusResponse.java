package com.roots.bff_server.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response of {@code GET /api/auth/status}. When the session has no valid login the
 * claim fields are omitted entirely ({@code NON_NULL}), leaving just
 * {@code {"isLoggedIn": false}}.
 */
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
