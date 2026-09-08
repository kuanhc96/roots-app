package com.roots.web_client_bff.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginStatusResponse(
        @JsonProperty("isLoggedIn") boolean isLoggedIn,
        IdTokenClaimsResponse idTokenClaims) {

    public static LoginStatusResponse loggedIn(IdTokenClaimsResponse idTokenClaims) {
        return new LoginStatusResponse(true, idTokenClaims);
    }

    public static LoginStatusResponse notLoggedIn() {
        return new LoginStatusResponse(false, null);
    }
}
