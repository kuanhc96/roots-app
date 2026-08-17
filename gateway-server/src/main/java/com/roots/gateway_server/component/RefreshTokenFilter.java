package com.roots.gateway_server.component;

import com.roots.gateway_server.utility.SessionIdExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenFilter implements GatewayFilter {

    private final RedisClient redisClient;
    private final AuthServerClient authServerClient;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.justOrEmpty(SessionIdExtractor.extractSessionId(exchange.getRequest()))
                .flatMap(sessionId -> {
                    if (redisClient.getAccessToken(sessionId).isPresent()) {
                        return Mono.empty();
                    }

                    return Mono.justOrEmpty(redisClient.getRefreshToken(sessionId))
                            .flatMap(authServerClient::exchangeRefreshToken)
                            .doOnNext(tokens -> redisClient.storeRefreshedTokens(sessionId, tokens))
                            .onErrorResume(WebClientResponseException.class, ex -> {
                                log.warn("Refresh token exchange failed with status {} for session {}", ex.getStatusCode(), sessionId);
                                return Mono.empty();
                            })
                            .onErrorResume(WebClientRequestException.class, ex -> {
                                log.warn("Unable to reach auth-server for refresh token exchange for session {}", sessionId);
                                return Mono.empty();
                            })
                            .then();
                })
                .then(chain.filter(exchange));
    }
}
