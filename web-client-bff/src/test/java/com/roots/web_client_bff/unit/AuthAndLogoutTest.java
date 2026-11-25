package com.roots.web_client_bff.unit;

import com.roots.web_client_bff.component.AuthorizedClientLogoutHandler;
import com.roots.web_client_bff.controller.AuthController;
import com.roots.web_client_bff.dto.response.LoginStatusResponse;
import com.roots.web_client_bff.service.LogoutService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthAndLogoutTest {
    @Test
    void statusRejectsAnonymousUnauthenticatedAndNonOidc() {
        AuthController controller = new AuthController();
        assertThat(controller.getLoginStatus(null)).isEqualTo(LoginStatusResponse.notLoggedIn());
        assertThat(controller.getLoginStatus(UsernamePasswordAuthenticationToken.unauthenticated("a", "b")))
                .isEqualTo(LoginStatusResponse.notLoggedIn());
        assertThat(controller.getLoginStatus(UsernamePasswordAuthenticationToken.authenticated("a", "b", List.of())))
                .isEqualTo(LoginStatusResponse.notLoggedIn());
    }

    @Test
    void statusReturnsGuestAndMemberClaims() {
        AuthController controller = new AuthController();
        OidcUser user = mock(OidcUser.class);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
        when(user.getEmail()).thenReturn("guest", "person@example.com");
        when(user.getClaimAsString("userGUID")).thenReturn(null, "guid");
        when(user.getClaimAsStringList("roles")).thenReturn(List.of("GUEST"), List.of("MEMBER", "DEACON"));
        assertThat(controller.getLoginStatus(authentication))
                .isEqualTo(LoginStatusResponse.loggedIn("guest", null, List.of("GUEST")));
        assertThat(controller.getLoginStatus(authentication))
                .isEqualTo(LoginStatusResponse.loggedIn("person@example.com", "guid", List.of("MEMBER", "DEACON")));
    }

    @Test
    void logoutRedirectContainsClientAndOptionalIdToken() throws Exception {
        LogoutService service = new LogoutService();
        ReflectionTestUtils.setField(service, "authServerExternalLocation", "https://auth.example");
        ReflectionTestUtils.setField(service, "clientId", "client");
        ReflectionTestUtils.setField(service, "clientOrigin", "https://web.example");
        var noHint = UriComponentsBuilder.fromUri(service.buildLogoutRedirect(null)).build().getQueryParams();
        assertThat(noHint.getFirst("client_id")).isEqualTo("client");
        assertThat(noHint.getFirst("post_logout_redirect_uri")).isEqualTo("https://web.example/logout");
        assertThat(noHint).doesNotContainKey("id_token_hint");

        OidcUser user = mock(OidcUser.class);
        when(user.getIdToken()).thenReturn(new OidcIdToken("id.token.value", Instant.now(),
                Instant.now().plusSeconds(60), Map.of("sub", "guest")));
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.onLogoutSuccess(new MockHttpServletRequest(), response, authentication);
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo(service.buildLogoutRedirect(authentication).toString());
        assertThat(UriComponentsBuilder.fromUri(service.buildLogoutRedirect(authentication)).build()
                .getQueryParams().getFirst("id_token_hint")).isEqualTo("id.token.value");
    }

    @Test
    void logoutHandlerUsesSessionAuthenticationOverRequestAuthentication() {
        OAuth2AuthorizedClientService clients = mock(OAuth2AuthorizedClientService.class);
        AuthorizedClientLogoutHandler handler = new AuthorizedClientLogoutHandler(clients);
        OAuth2AuthenticationToken stored = mock(OAuth2AuthenticationToken.class);
        when(stored.getAuthorizedClientRegistrationId()).thenReturn("web-client-registration");
        when(stored.getName()).thenReturn("guest");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(stored));
        handler.logout(request, new MockHttpServletResponse(),
                UsernamePasswordAuthenticationToken.authenticated("other", null, List.of()));
        verify(clients).removeAuthorizedClient("web-client-registration", "guest");
    }

    @Test
    void logoutHandlerUsesArgumentWithoutSessionAndIgnoresNonOauth() {
        OAuth2AuthorizedClientService clients = mock(OAuth2AuthorizedClientService.class);
        AuthorizedClientLogoutHandler handler = new AuthorizedClientLogoutHandler(clients);
        MockHttpServletRequest request = new MockHttpServletRequest();
        OAuth2AuthenticationToken token = mock(OAuth2AuthenticationToken.class);
        when(token.getAuthorizedClientRegistrationId()).thenReturn("registration");
        when(token.getName()).thenReturn("person");
        handler.logout(request, new MockHttpServletResponse(), token);
        verify(clients).removeAuthorizedClient("registration", "person");
        handler.logout(request, new MockHttpServletResponse(), null);
        verifyNoMoreInteractions(clients);
        assertThat(request.getSession(false)).isNull();
    }
}
