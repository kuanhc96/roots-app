package com.roots.account_management_client_bff.unit;

import com.roots.account_management_client_bff.config.SpaFallbackConfig;
import com.roots.account_management_client_bff.controller.AuthController;
import com.roots.account_management_client_bff.dto.response.LoginStatusResponse;
import com.roots.account_management_client_bff.service.AuthCallbackService;
import com.roots.account_management_client_bff.service.AuthStatusService;
import com.roots.account_management_client_bff.service.AuthorizeService;
import com.roots.account_management_client_bff.service.LogoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.net.URI;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitWebConfig(SpaRoutingTest.WebConfig.class)
class SpaRoutingTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AuthStatusService authStatus;
    @Autowired
    private LogoutService logout;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void servesSpaForHomeAndLogout() throws Exception {
        for (String path : new String[]{"/index.html", "/logout", "/client-side-route"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString().trim())
                            .isEqualTo("<html><body>test SPA shell</body></html>"));
        }
    }

    @Test
    void servesRealAssetsInsteadOfSpaShell() throws Exception {
        mvc.perform(get("/routing-test.txt"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString().trim())
                        .isEqualTo("test static asset"));
    }

    @Test
    void apiControllerTakesPrecedenceOverSpaFallback() throws Exception {
        when(authStatus.getLoginStatus(anyString())).thenReturn(LoginStatusResponse.notLoggedIn());
        mvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"isLoggedIn\":false}"));
    }

    @Test
    void logoutEndpointInvalidatesSessionAndRedirectsRatherThanServingSpa() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(logout.buildLogoutRedirect(session.getId())).thenReturn(URI.create("http://localhost:9000/connect/logout"));
        mvc.perform(get("/api/auth/logout").session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:9000/connect/logout"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Configuration
    @EnableWebMvc
    @Import({SpaFallbackConfig.class, AuthController.class})
    static class WebConfig {
        @Bean
        AuthStatusService authStatusService() {
            return mock(AuthStatusService.class);
        }

        @Bean
        AuthorizeService authorizeService() {
            return mock(AuthorizeService.class);
        }

        @Bean
        AuthCallbackService authCallbackService() {
            return mock(AuthCallbackService.class);
        }

        @Bean
        LogoutService logoutService() {
            return mock(LogoutService.class);
        }
    }
}
