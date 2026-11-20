package com.roots.account_management_client_bff.component;

import com.roots.account_management_client_bff.dto.response.TokenResponse;
import com.roots.account_management_client_bff.service.TokenStoreService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final TokenStoreService tokenStore;
    private final OAuth2AuthorizedClientRepository authorizedClients;

    @Value("${account-management.client.origin}")
    private String clientOrigin;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        Assert.isInstanceOf(OAuth2AuthenticationToken.class, authentication);
        OAuth2AuthenticationToken oauth2Authentication = (OAuth2AuthenticationToken) authentication;
        Assert.isInstanceOf(OidcUser.class, authentication.getPrincipal());
        OidcUser user = (OidcUser) authentication.getPrincipal();
        String registrationId = oauth2Authentication.getAuthorizedClientRegistrationId();
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(
                registrationId, authentication, request);
        Assert.state(client != null, "Successful OIDC login has no authorized client");

        // Session fixation protection has already run, so tokens use the final session id.
        tokenStore.storeTokenResponse(request.getSession().getId(), new TokenResponse(
                client.getAccessToken().getTokenValue(),
                client.getRefreshToken() == null ? null : client.getRefreshToken().getTokenValue(),
                user.getIdToken().getTokenValue()));
        // The existing token store remains authoritative for refresh and back-channel logout.
        authorizedClients.removeAuthorizedClient(registrationId, authentication, request, response);
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, clientOrigin + "/");
    }
}
