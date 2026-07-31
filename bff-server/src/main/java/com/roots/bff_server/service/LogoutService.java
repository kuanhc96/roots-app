package com.roots.bff_server.service;

import com.roots.bff_server.enums.TokenType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

/**
 * Drives RP-Initiated Logout on behalf of web-client: clears the session's tokens
 * from Redis and builds the auth-server {@code /connect/logout} URL the browser is
 * redirected to. Uses the externally reachable auth-server base URL — the browser
 * follows this redirect from outside the docker network, where the internal hostname
 * doesn't resolve (same reasoning as {@link AuthorizeService}).
 *
 * <p>{@code client_id} is always sent so auth-server can identify the client and
 * therefore honour {@code post_logout_redirect_uri} even when the id_token (and thus
 * {@code id_token_hint}) is absent — a login revived via refresh-only never has one
 * stored. When the id_token is present it rides along as {@code id_token_hint}: with
 * it, Spring's OIDC logout skips the confirmation page and redirects straight back.
 */
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final TokenStoreService tokenStore;

    // Field injection (not constructor args) so @RequiredArgsConstructor keeps wiring
    // the final dependencies (same pattern as AuthorizeService).
    @Value("${auth-server.external-location}")
    private String authServerExternalLocation;
    @Value("${web.client.id}")
    private String clientId;
    @Value("${web.client.origin}")
    private String webClientOrigin;

    public URI buildLogoutRedirect(String sessionId) {
        // Read the id_token for the hint before clearing — clearTokens deletes it.
        Optional<String> idToken = tokenStore.find(sessionId, TokenType.ID_TOKEN);
        tokenStore.clearTokens(sessionId);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authServerExternalLocation)
                .path("/connect/logout")
                .queryParam("client_id", clientId)
                .queryParam("post_logout_redirect_uri", webClientOrigin + "/logout");
        idToken.ifPresent(token -> builder.queryParam("id_token_hint", token));

        return builder.encode().build().toUri();
    }
}
