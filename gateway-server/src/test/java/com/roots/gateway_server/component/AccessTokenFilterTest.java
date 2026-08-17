package com.roots.gateway_server.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class AccessTokenFilterTest {

    @Test
    void addsAuthorizationHeaderForSimpleResourceRoute() {
        RedisClient redisClient = mock(RedisClient.class);
        AccessTokenFilter filter = new AccessTokenFilter(redisClient);

        String sessionId = "session-1";
        String encodedSession = Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
        when(redisClient.getAccessToken(sessionId)).thenReturn(Optional.of("token-123"));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/simple-resource-server/api/role/member")
                .cookie(new HttpCookie("__Host-SESSION", encodedSession))
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        GatewayFilterChain chain = capturedHeaderChain(authorizationHeader);

        filter.filter(exchange, chain).block();

        assertThat(authorizationHeader.get()).isEqualTo("Bearer token-123");
    }

    @Test
    void leavesNonSimpleResourceRouteUnchanged() {
        RedisClient redisClient = mock(RedisClient.class);
        AccessTokenFilter filter = new AccessTokenFilter(redisClient);

        MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/bff-server/api/auth/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer incoming")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        GatewayFilterChain chain = capturedHeaderChain(authorizationHeader);

        filter.filter(exchange, chain).block();

        verifyNoInteractions(redisClient);
        assertThat(authorizationHeader.get()).isEqualTo("Bearer incoming");
    }

    @Test
    void leavesRequestUnchangedWhenTokenNotFound() {
        RedisClient redisClient = mock(RedisClient.class);
        AccessTokenFilter filter = new AccessTokenFilter(redisClient);

        String sessionId = "session-2";
        String encodedSession = Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
        when(redisClient.getAccessToken(sessionId)).thenReturn(Optional.empty());

        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/simple-resource-server/api/role/member")
                .cookie(new HttpCookie("__Host-SESSION", encodedSession))
                .header(HttpHeaders.AUTHORIZATION, "Bearer incoming")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        GatewayFilterChain chain = capturedHeaderChain(authorizationHeader);

        filter.filter(exchange, chain).block();

        assertThat(authorizationHeader.get()).isEqualTo("Bearer incoming");
    }

    private static GatewayFilterChain capturedHeaderChain(AtomicReference<String> authorizationHeader) {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange forwardedExchange = invocation.getArgument(0);
            authorizationHeader.set(forwardedExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.empty();
        });
        return chain;
    }
}
