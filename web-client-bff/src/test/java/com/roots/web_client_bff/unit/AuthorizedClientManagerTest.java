package com.roots.web_client_bff.unit;

import com.roots.web_client_bff.client.SimpleResourceClient;
import com.roots.web_client_bff.service.RoleProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthorizedClientManagerTest {
    private OAuth2AuthorizedClientRepository repository;
    private OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> tokenClient;
    private SimpleResourceClient downstream;
    private OAuth2AuthenticationToken principal;
    private ClientRegistration registration;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RoleProxyService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        registration = ClientRegistration.withRegistrationId("web-client-registration")
                .clientId("client").clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://auth.example/authorize").tokenUri("https://auth.example/token")
                .redirectUri("https://web.example/callback").build();
        repository = mock(OAuth2AuthorizedClientRepository.class);
        tokenClient = mock(OAuth2AccessTokenResponseClient.class);
        downstream = mock(SimpleResourceClient.class);
        OAuth2User user = mock(OAuth2User.class);
        when(user.getName()).thenReturn("guest");
        principal = new OAuth2AuthenticationToken(user, List.of(), "web-client-registration");
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        DefaultOAuth2AuthorizedClientManager manager = new DefaultOAuth2AuthorizedClientManager(
                new InMemoryClientRegistrationRepository(registration), repository);
        RefreshTokenOAuth2AuthorizedClientProvider refresh = new RefreshTokenOAuth2AuthorizedClientProvider();
        refresh.setAccessTokenResponseClient(tokenClient);
        refresh.setClockSkew(Duration.ZERO);
        manager.setAuthorizedClientProvider(refresh);
        service = new RoleProxyService(manager, downstream);
    }

    @Test
    void managerReusesValidAccessTokenWithoutRefreshing() {
        when(repository.loadAuthorizedClient(anyString(), any(), any())).thenReturn(stored(300));
        when(downstream.getRole("guest", "old-access")).thenReturn(ResponseEntity.ok("guest"));
        assertThat(service.forward(principal, request, response, "guest").getBody()).isEqualTo("guest");
        verifyNoInteractions(tokenClient);
        verify(repository, never()).saveAuthorizedClient(any(), any(), any(), any());
    }

    @Test
    void managerRefreshesExpiredAccessTokenAndPersistsRotatedToken() {
        when(repository.loadAuthorizedClient(anyString(), any(), any())).thenReturn(stored(-1));
        when(tokenClient.getTokenResponse(any())).thenReturn(OAuth2AccessTokenResponse.withToken("new-access")
                .tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(300).refreshToken("new-refresh").build());
        when(downstream.getRole("guest", "new-access")).thenReturn(ResponseEntity.ok("guest"));
        assertThat(service.forward(principal, request, response, "guest").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(tokenClient).getTokenResponse(argThat(grant ->
                grant.getRefreshToken().getTokenValue().equals("old-refresh")));
        verify(repository).saveAuthorizedClient(argThat(client ->
                client.getAccessToken().getTokenValue().equals("new-access")
                        && client.getRefreshToken().getTokenValue().equals("new-refresh")),
                same(principal), same(request), same(response));
    }

    @Test
    void managerRemovesClientAfterInvalidGrant() {
        when(repository.loadAuthorizedClient(anyString(), any(), any())).thenReturn(stored(-1));
        when(tokenClient.getTokenResponse(any()))
                .thenThrow(new OAuth2AuthorizationException(new OAuth2Error("invalid_grant")));
        assertThat(service.forward(principal, request, response, "guest").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(repository).removeAuthorizedClient(eq("web-client-registration"), same(principal),
                same(request), same(response));
        verifyNoInteractions(downstream);
    }

    private OAuth2AuthorizedClient stored(long expirySeconds) {
        Instant expiry = Instant.now().plusSeconds(expirySeconds);
        return new OAuth2AuthorizedClient(registration, "guest",
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "old-access",
                        expiry.minusSeconds(600), expiry),
                new OAuth2RefreshToken("old-refresh", expiry.minusSeconds(600)));
    }
}
