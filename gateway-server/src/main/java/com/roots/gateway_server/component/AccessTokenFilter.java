package com.roots.gateway_server.component;

import java.util.Optional;

import com.roots.gateway_server.utility.SessionIdExtractor;
import lombok.RequiredArgsConstructor;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AccessTokenFilter implements GatewayFilter {

    private static final String AUTHORIZATION_BEARER_PREFIX = "Bearer ";

    private final RedisClient redisClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        Optional<String> accessToken = SessionIdExtractor.extractSessionId(request)
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
}
