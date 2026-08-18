package com.roots.gateway_server.utility;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

public class TraceUtility {
    public static final String TRACE_ID = "roots-app-trace-id";

    private TraceUtility() {}

    public static String getTraceId(HttpHeaders requestHeaders) {
        if (requestHeaders.get(TRACE_ID) != null) {
            List<String> requestHeaderList = requestHeaders.get(TRACE_ID);
            return requestHeaderList.stream().findFirst().get();
        } else {
            return null;
        }
    }

    public static ServerWebExchange setRequestHeader(ServerWebExchange exchange, String name, String value) {
        return exchange.mutate().request(exchange.getRequest().mutate().header(name, value).build()).build();
    }

    public static ServerWebExchange setTraceId(ServerWebExchange exchange, String traceId) {
        return setRequestHeader(exchange, TRACE_ID, traceId);
    }

    public static String generateTraceId() {
        return java.util.UUID.randomUUID().toString();
    }
}
