package com.roots.web_client_bff.config;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.SessionTrackingMode;
import java.util.EnumSet;

@Configuration
public class ServletContainerSessionConfig {

    @Bean
    public ServletContextInitializer cookieOnlySessionTracking() {
        return servletContext -> servletContext.setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
    }
}
