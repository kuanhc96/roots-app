package com.roots.gateway_server.component;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.roots.gateway_server.dto.response.TokenExchangeResponse;
import reactor.core.publisher.Mono;

class RefreshTokenFilterTest {

    @Test
    void exchangesRefreshTokenWhenAccessTokenMissing() {
        RedisClient redisClient = mock(RedisClient.class);
        AuthServerClient authServerClient = mock(AuthServerClient.class);
        RefreshTokenFilter filter = new RefreshTokenFilter(redisClient, authServerClient);

        String sessionId = "session-1";
        when(redisClient.getAccessToken(sessionId)).thenReturn(Optional.empty());
        when(redisClient.getRefreshToken(sessionId)).thenReturn(Optional.of("refresh-123"));
        TokenExchangeResponse tokenExchangeResponse =
                new TokenExchangeResponse("access-456", 3600, "refresh-456", "id-456");
        when(authServerClient.exchangeRefreshToken("refresh-123")).thenReturn(Mono.just(tokenExchangeResponse));

        ServerWebExchange exchange = exchangeWithSessionCookie(sessionId);
        GatewayFilterChain chain = chainThatTracksInvocation();

        filter.filter(exchange, chain).block();

        verify(authServerClient).exchangeRefreshToken("refresh-123");
        verify(redisClient).storeRefreshedTokens(sessionId, tokenExchangeResponse);
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void continuesWithoutExchangeWhenAccessTokenExists() {
        RedisClient redisClient = mock(RedisClient.class);
        AuthServerClient authServerClient = mock(AuthServerClient.class);
        RefreshTokenFilter filter = new RefreshTokenFilter(redisClient, authServerClient);

        String sessionId = "session-2";
        when(redisClient.getAccessToken(sessionId)).thenReturn(Optional.of("access-123"));

        ServerWebExchange exchange = exchangeWithSessionCookie(sessionId);
        GatewayFilterChain chain = chainThatTracksInvocation();

        filter.filter(exchange, chain).block();

        verify(authServerClient, never()).exchangeRefreshToken(any());
        verify(redisClient, never()).storeRefreshedTokens(any(), any());
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void continuesWithoutExchangeWhenRefreshTokenMissing() {
        RedisClient redisClient = mock(RedisClient.class);
        AuthServerClient authServerClient = mock(AuthServerClient.class);
        RefreshTokenFilter filter = new RefreshTokenFilter(redisClient, authServerClient);

        String sessionId = "session-3";
        when(redisClient.getAccessToken(sessionId)).thenReturn(Optional.empty());
        when(redisClient.getRefreshToken(sessionId)).thenReturn(Optional.empty());

        ServerWebExchange exchange = exchangeWithSessionCookie(sessionId);
        GatewayFilterChain chain = chainThatTracksInvocation();

        filter.filter(exchange, chain).block();

        verify(authServerClient, never()).exchangeRefreshToken(any());
        verify(redisClient, never()).storeRefreshedTokens(any(), any());
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void continuesWhenAuthServerRejectsRefreshToken() {
        RedisClient redisClient = mock(RedisClient.class);
        AuthServerClient authServerClient = mock(AuthServerClient.class);
        RefreshTokenFilter filter = new RefreshTokenFilter(redisClient, authServerClient);

        String sessionId = "session-4";
        when(redisClient.getAccessToken(sessionId)).thenReturn(Optional.empty());
        when(redisClient.getRefreshToken(sessionId)).thenReturn(Optional.of("refresh-123"));
        when(authServerClient.exchangeRefreshToken("refresh-123"))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        400,
                        "Bad Request",
                        null,
                        new byte[0],
                        StandardCharsets.UTF_8
                )));

        ServerWebExchange exchange = exchangeWithSessionCookie(sessionId);
        GatewayFilterChain chain = chainThatTracksInvocation();

        filter.filter(exchange, chain).block();

        verify(redisClient, never()).storeRefreshedTokens(any(), any());
        verify(chain).filter(any(ServerWebExchange.class));
    }

    private static ServerWebExchange exchangeWithSessionCookie(String sessionId) {
        String encodedSession = Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost:8080/simple-resource-server/api/role/member")
                .cookie(new HttpCookie("__Host-SESSION", encodedSession))
                .build();
        return MockServerWebExchange.from(request);
    }

    private static GatewayFilterChain chainThatTracksInvocation() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        return chain;
    }
}
