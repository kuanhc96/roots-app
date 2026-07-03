package com.roots.authserver.config;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

import com.roots.authserver.controller.SpaController;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(info = @Info(
        title = "auth-server API",
        description = "OAuth2 Authorization Server with form login, MFA (one-time token), "
                + "account creation with magic-link email verification, and forgot-password reset. "
                + "The OAuth2/OIDC protocol endpoints (/oauth2/*, /connect/*) are provided by "
                + "Spring Authorization Server and are not listed here.",
        version = "v1"
))
@Configuration
public class OpenApiConfig {

    static {
        // SpaController is a view-forwarding @Controller (no @ResponseBody), which
        // springdoc skips by default; register it so the browser-facing login-flow
        // endpoints it hosts appear in the API docs.
        SpringDocUtils.getConfig().addRestControllers(SpaController.class);
    }
}
