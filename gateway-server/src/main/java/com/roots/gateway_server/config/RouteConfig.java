package com.roots.gateway_server.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class RouteConfig {

    @Bean
    public RouteLocator routeFilterConfig(RouteLocatorBuilder routeLocatorBuilder) {
        return routeLocatorBuilder.routes()
                .route(p -> p
                        .path("/roots-app/simple-resource-server/**")
                        .filters(f -> f
                                .rewritePath("/roots-app/simple-resource-server/(?<segment>.*)", "/api/$\\{segment}")
                        )
                        .uri("lb://SIMPLE-RESOURCE-SERVER")
                )
                .route(p -> p
                        .path("/roots-app/account-management/**")
                        .filters(f -> f
                                .rewritePath("/roots-app/account-management/(?<segment>.*)", "/api/$\\{segment}")
                        )
                        .uri("lb://ACCOUNT-MANAGEMENT")
                )
                .build();
    }
}
