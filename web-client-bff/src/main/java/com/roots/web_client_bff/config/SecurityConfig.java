package com.roots.web_client_bff.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    private static final String WEB_CLIENT_REGISTRATION_ID = "web-client-pkce-registration";

    @Autowired
    private InMemoryReactiveClientRegistrationRepository clientRegistrationRepository;

    @Bean
    public WebClient simpleResourceServerWebClient(ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2 = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2.setDefaultClientRegistrationId(WEB_CLIENT_REGISTRATION_ID);

        return WebClient.builder()
                .filter(oauth2)
                .build();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/role/**").authenticated()
                        .anyExchange().permitAll()
                )
                .oauth2Login(oauth2LoginSpec -> oauth2LoginSpec
                        .authenticationMatcher(
                                new PathPatternParserServerWebExchangeMatcher("/login/oauth2/code/{registrationId}")
                        )
                        .authenticationSuccessHandler(
                                new RedirectServerAuthenticationSuccessHandler("/home")
                        )
                        .authenticationFailureHandler(
                                new RedirectServerAuthenticationFailureHandler("/?e=login_failed")
                        )
                )
                .logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler()))
                .oidcLogout(logout -> logout.backChannel(Customizer.withDefaults()))
                .oauth2Client(Customizer.withDefaults());
        return http.build();
    }

    private ServerLogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedServerLogoutSuccessHandler oidcLogoutSuccessHandler = new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("/logout");
        return oidcLogoutSuccessHandler;
    }
}
