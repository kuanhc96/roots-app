package com.roots.gateway_server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "web.client.origin=http://localhost:8083",
        "account-management.client.origin=http://localhost:8084"
})
class GatewayServerApplicationTests {
    @Autowired
    private ApplicationContext context;

    @Test
    void startsWithoutRedisAndKeepsResourceRoutes() {
        assertThat(ClassUtils.isPresent("org.springframework.data.redis.connection.RedisConnectionFactory",
                context.getClassLoader())).isFalse();
        assertThat(context.getBean("routeFilterConfig", RouteLocator.class).getRoutes()
                .map(route -> route.getUri().toString()).collectList().block())
                .containsExactly("lb://SIMPLE-RESOURCE-SERVER", "lb://ACCOUNT-MANAGEMENT");
    }
}
