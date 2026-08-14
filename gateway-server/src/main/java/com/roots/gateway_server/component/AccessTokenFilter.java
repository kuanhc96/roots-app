package com.roots.gateway_server.component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AccessTokenFilter implements GlobalFilter {

    private static final String SESSION_COOKIE_NAME = "__Host-SESSION";
    private static final String SIMPLE_RESOURCE_ROUTE_PREFIX = "/simple-resource-server/";
    private static final String SIMPLE_RESOURCE_ROUTE_EXACT = "/simple-resource-server";
    private static final String AUTHORIZATION_BEARER_PREFIX = "Bearer ";

    private final RedisClient redisClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        Optional<String> accessToken = extractSessionId(request)
                .flatMap(redisClient::getAccessToken);

        if (accessToken.isEmpty()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest enrichedRequest = request.mutate()
                .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION,
                        AUTHORIZATION_BEARER_PREFIX + accessToken.get()))
                .build();

        return chain.filter(exchange.mutate().request(enrichedRequest).build());
    }

    private static boolean isSimpleResourceRoute(String path) {
        return SIMPLE_RESOURCE_ROUTE_EXACT.equals(path) || path.startsWith(SIMPLE_RESOURCE_ROUTE_PREFIX);
    }

    private static Optional<String> extractSessionId(ServerHttpRequest request) {
        HttpCookie sessionCookie = request.getCookies().getFirst(SESSION_COOKIE_NAME);
        if (sessionCookie == null || sessionCookie.getValue() == null || sessionCookie.getValue().isBlank()) {
            return Optional.empty();
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(sessionCookie.getValue());
            return Optional.of(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
