package com.roots.web_client_bff.unit;

import com.roots.web_client_bff.client.SimpleResourceClient;
import com.roots.web_client_bff.service.RoleProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoleProxyServiceTest {
    private OAuth2AuthorizedClientManager manager;
    private SimpleResourceClient downstream;
    private RoleProxyService service;
    private OAuth2AuthenticationToken authentication;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        manager = mock(OAuth2AuthorizedClientManager.class);
        downstream = mock(SimpleResourceClient.class);
        service = new RoleProxyService(manager, downstream);
        authentication = mock(OAuth2AuthenticationToken.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getAuthorizedClientRegistrationId()).thenReturn("web-client-registration");
    }

    @Test
    void passesManagerReturnedTokenAndHttpContextAndPreservesDownstreamResponse() {
        OAuth2AuthorizedClient client = client("existing-token", Instant.now().plusSeconds(300));
        when(manager.authorize(any())).thenReturn(client);
        ResponseEntity<String> downstreamResponse = ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.TEXT_PLAIN).body("denied");
        when(downstream.getRole("pastor", "existing-token")).thenReturn(downstreamResponse);

        assertThat(service.forward(authentication, request, response, "pastor")).isSameAs(downstreamResponse);
        ArgumentCaptor<OAuth2AuthorizeRequest> captor = ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);
        verify(manager).authorize(captor.capture());
        assertThat(captor.getValue().getClientRegistrationId()).isEqualTo("web-client-registration");
        assertThat(captor.getValue().getPrincipal()).isSameAs(authentication);
        assertThat((Object) captor.getValue().getAttribute(jakarta.servlet.http.HttpServletRequest.class.getName()))
                .isSameAs(request);
        assertThat((Object) captor.getValue().getAttribute(jakarta.servlet.http.HttpServletResponse.class.getName()))
                .isSameAs(response);
    }

    @Test
    void usesRefreshedTokenReturnedByManager() {
        OAuth2AuthorizedClient refreshed = client("refreshed-token", Instant.now().plusSeconds(300));
        when(manager.authorize(any())).thenReturn(refreshed);
        when(downstream.getRole("guest", "refreshed-token")).thenReturn(ResponseEntity.ok("I am a guest"));
        assertThat(service.forward(authentication, request, response, "guest").getBody()).isEqualTo("I am a guest");
        verify(downstream).getRole("guest", "refreshed-token");
    }

    @Test
    void rejectsMissingOrWrongAuthenticationBeforeCallingManager() {
        assertUnauthorized(service.forward(null, request, response, "guest"));
        assertUnauthorized(service.forward(UsernamePasswordAuthenticationToken.authenticated("user", "pw", java.util.List.of()),
                request, response, "guest"));
        when(authentication.isAuthenticated()).thenReturn(false);
        assertUnauthorized(service.forward(authentication, request, response, "guest"));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("other");
        assertUnauthorized(service.forward(authentication, request, response, "guest"));
        verifyNoInteractions(manager, downstream);
    }

    @Test
    void rejectsMissingClientMissingTokenAndExpiredToken() {
        OAuth2AuthorizedClient empty = mock(OAuth2AuthorizedClient.class);
        OAuth2AuthorizedClient expired = client("expired", Instant.now().minusSeconds(10));
        when(manager.authorize(any())).thenReturn(null, empty, expired);
        for (int i = 0; i < 3; i++)
            assertUnauthorized(service.forward(authentication, request, response, "guest"));
        verifyNoInteractions(downstream);
    }

    @Test
    void rejectsInvalidGrantButPropagatesOtherAuthorizationErrors() {
        OAuth2AuthorizationException invalidGrant = new OAuth2AuthorizationException(new OAuth2Error("invalid_grant"));
        OAuth2AuthorizationException other = new OAuth2AuthorizationException(new OAuth2Error("server_error"));
        when(manager.authorize(any())).thenThrow(invalidGrant, other);
        assertUnauthorized(service.forward(authentication, request, response, "guest"));
        assertThatThrownBy(() -> service.forward(authentication, request, response, "guest")).isSameAs(other);
        verifyNoInteractions(downstream);
    }

    private static OAuth2AuthorizedClient client(String value, Instant expiry) {
        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        when(client.getAccessToken()).thenReturn(new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, value, expiry.minusSeconds(60), expiry));
        return client;
    }

    private static void assertUnauthorized(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
