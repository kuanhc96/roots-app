package com.roots.authserver.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The subset of Google's {@code POST /token} response {@link com.roots.authserver.service.SocialLoginService}
 * needs — only the id_token is verified and used; access/refresh tokens are discarded.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleTokenResponse(@JsonProperty("id_token") String idToken) {
}
