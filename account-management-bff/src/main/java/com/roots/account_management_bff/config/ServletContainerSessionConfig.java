package com.roots.account_management_bff.config;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class ServletContainerSessionConfig {

    @Bean
    public ServletContextInitializer disableServletContainerSessionTracking() {
        return servletContext -> servletContext.setSessionTrackingModes(Collections.emptySet());
    }
}
