package com.roots.gateway_server.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.roots.gateway_server.component.AccessTokenFilter;
import com.roots.gateway_server.component.RefreshTokenFilter;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class RouteConfig {

    private final RefreshTokenFilter refreshTokenFilter;
    private final AccessTokenFilter accessTokenFilter;

    @Bean
    public RouteLocator routeFilterConfig(RouteLocatorBuilder routeLocatorBuilder) {
        return routeLocatorBuilder.routes()
                .route(p -> p
                        .path("/roots-app/simple-resource-server/**")
                        .filters(f -> f
                                .filter(refreshTokenFilter)
                                .filter(accessTokenFilter)
                                .rewritePath("/roots-app/simple-resource-server/(?<segment>.*)", "/api/$\\{segment}")
                        )
                        .uri("lb://SIMPLE-RESOURCE-SERVER")
                )
                .route(p -> p
                        .path("/roots-app/bff-server/**")
                        .filters(f -> f
                                .rewritePath("/roots-app/bff-server/(?<segment>.*)", "/api/$\\{segment}")
                        )
                        .uri("lb://BFF-SERVER")
                )
                .build();
    }
}
