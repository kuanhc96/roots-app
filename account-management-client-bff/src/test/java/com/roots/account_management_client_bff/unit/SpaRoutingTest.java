package com.roots.account_management_client_bff.unit;

import com.roots.account_management_client_bff.config.SpaFallbackConfig;
import com.roots.account_management_client_bff.controller.AuthController;
import com.roots.account_management_client_bff.dto.response.LoginStatusResponse;
import com.roots.account_management_client_bff.service.AuthStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitWebConfig(SpaRoutingTest.WebConfig.class)
class SpaRoutingTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AuthStatusService authStatus;
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
        when(authStatus.getLoginStatus(isNull())).thenReturn(LoginStatusResponse.notLoggedIn());
        mvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"isLoggedIn\":false}"));
    }

    @Configuration
    @EnableWebMvc
    @Import({SpaFallbackConfig.class, AuthController.class})
    static class WebConfig {
        @Bean
        AuthStatusService authStatusService() {
            return mock(AuthStatusService.class);
        }

    }
}
