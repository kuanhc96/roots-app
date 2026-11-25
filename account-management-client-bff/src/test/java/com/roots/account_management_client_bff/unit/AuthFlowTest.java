package com.roots.account_management_client_bff.unit;

import com.roots.account_management_client_bff.service.LogoutService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthFlowTest {

    @Test
    void logoutUsesSessionIdTokenAndReturnsToEmbeddedLogout() {
        LogoutService logout = new LogoutService();
        ReflectionTestUtils.setField(logout, "authServerExternalLocation", "http://localhost:9000");
        ReflectionTestUtils.setField(logout, "clientId", "ACCOUNT_MANAGEMENT_CLIENT");
        ReflectionTestUtils.setField(logout, "accountManagementClientOrigin", "http://localhost:8084");
        OidcUser user = mock(OidcUser.class);
        when(user.getName()).thenReturn("user");
        when(user.getIdToken()).thenReturn(new OidcIdToken("id-token", Instant.now(),
                Instant.now().plusSeconds(300), Map.of("sub", "user")));
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                user, List.of(), "account-management-client-registration");

        URI redirect = logout.buildLogoutRedirect(authentication);
        var query = UriComponentsBuilder.fromUri(redirect).build().getQueryParams();
        assertThat(redirect.getPath()).isEqualTo("/connect/logout");
        assertThat(query.getFirst("post_logout_redirect_uri")).isEqualTo("http://localhost:8084/logout");
        assertThat(query.getFirst("client_id")).isEqualTo("ACCOUNT_MANAGEMENT_CLIENT");
        assertThat(query.getFirst("id_token_hint")).isEqualTo("id-token");
    }
}
