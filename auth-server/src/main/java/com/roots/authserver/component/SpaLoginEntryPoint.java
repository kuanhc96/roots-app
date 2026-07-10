package com.roots.authserver.component;

import java.io.IOException;
import java.util.List;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SpaLoginEntryPoint implements AuthenticationEntryPoint {
    private static final List<String> PASSTHROUGH_PARAMS = List.of("client_id");

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        String originalUrl = request.getRequestURL().toString()
                + (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        MultiValueMap<String, String> originalParams =
                UriComponentsBuilder.fromUriString(originalUrl).build().getQueryParams();

        UriComponentsBuilder redirect = UriComponentsBuilder.fromPath("/login");
        for (String param : PASSTHROUGH_PARAMS) {
            String value = originalParams.getFirst(param);
            if (value != null) redirect.queryParam(param, value);
        }

        response.sendRedirect(redirect.build().toUriString());
    }
}
