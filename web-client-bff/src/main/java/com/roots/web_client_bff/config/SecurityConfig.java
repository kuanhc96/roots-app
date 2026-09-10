package com.roots.web_client_bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
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
                )
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .oauth2Client(Customizer.withDefaults());
        return http.build();
    }
}
