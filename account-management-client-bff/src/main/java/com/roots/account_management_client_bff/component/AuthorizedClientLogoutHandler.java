package com.roots.account_management_client_bff.component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthorizedClientLogoutHandler implements LogoutHandler {

    private final OAuth2AuthorizedClientService authorizedClients;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // Internal back-channel logout runs before the session's SecurityContext is loaded.
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) instanceof SecurityContext context) {
            authentication = context.getAuthentication();
        }
        if (authentication instanceof OAuth2AuthenticationToken oauth2) {
            authorizedClients.removeAuthorizedClient(oauth2.getAuthorizedClientRegistrationId(), oauth2.getName());
        }
    }
}
