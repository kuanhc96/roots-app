package com.roots.gateway_server.component;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.roots.gateway_server.utility.TraceUtility;
import reactor.core.publisher.Mono;

@Order(1)
@Component
public class RequestTraceFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
        if (!isTraceIdPresent(requestHeaders)) {
            String traceId = TraceUtility.generateTraceId();
            exchange = TraceUtility.setTraceId(exchange, traceId);
        }
        return chain.filter(exchange);
    }

    private boolean isTraceIdPresent(HttpHeaders requestHeaders) {
        return TraceUtility.getTraceId(requestHeaders) != null;

    }
}
