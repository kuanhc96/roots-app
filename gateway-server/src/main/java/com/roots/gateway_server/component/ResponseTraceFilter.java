package com.roots.gateway_server.component;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.roots.gateway_server.utility.TraceUtility;
import reactor.core.publisher.Mono;

@Component
public class ResponseTraceFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
            String traceId = TraceUtility.getTraceId(requestHeaders);
            exchange.getResponse().getHeaders().add(TraceUtility.TRACE_ID, traceId);
        }));
    }

}
