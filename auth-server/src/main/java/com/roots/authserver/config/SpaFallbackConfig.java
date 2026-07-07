package com.roots.authserver.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the embedded Nuxt SPA. The frontend is built with {@code ssr: false}, so a
 * single {@code index.html} shell mounts the whole app: real static assets
 * ({@code /_nuxt/*}, favicon, …) resolve to their files, and any other GET falls back
 * to the shell, where the client router owns the path ({@code /login},
 * {@code /forgot-password}, …). No per-page controller forwards are needed.
 *
 * <p>Everything with an explicit server-side owner still wins over this handler:
 * Spring Security filter endpoints ({@code /oauth2/**}, {@code /connect/**},
 * {@code POST /login}) run before MVC, and {@code @RequestMapping} handlers (REST
 * APIs, the form-post endpoints in {@code AuthFlowController}, actuator, springdoc)
 * take precedence over static-resource handling.
 *
 * <p>Caveat of that precedence: when a URL matches a {@code @RequestMapping} path
 * but not its HTTP method, Spring MVC responds 405 rather than falling through to
 * this handler. Page paths that share a URL with a form POST therefore need an
 * explicit GET forward to the shell — see {@code AuthFlowController#forwardSpaShell}.
 */
@Configuration
public class SpaFallbackConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
