package com.roots.web_client_bff.dto.response;

import java.util.List;

public record IdTokenClaimsResponse(
        String email,
        String userGUID,
        List<String> roles) {
}
