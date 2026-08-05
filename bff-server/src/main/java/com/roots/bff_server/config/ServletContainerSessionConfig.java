package com.roots.bff_server.config;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class ServletContainerSessionConfig {

    @Bean
    public ServletContextInitializer disableServletContainerSessionTracking() {
        // Spring Session owns session id resolution/cookies; disable container tracking
        // so Tomcat never emits JSESSIONID.
        return servletContext -> servletContext.setSessionTrackingModes(Collections.emptySet());
    }
}
