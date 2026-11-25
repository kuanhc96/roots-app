package com.roots.web_client_bff.unit;

import com.roots.web_client_bff.WebClientBffApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = WebClientBffApplication.class, properties = {
        "web.client.secret=secret", "web.client.origin=http://localhost:8083"
})
@Import(SecurityFlowTest.RegistrationConfig.class)
class SecurityFlowTest {
    @Autowired
    WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void authorizationUsesPkceAndStateMismatchFails() throws Exception {
        var start = mvc.perform(get("/oauth2/authorization/web-client-registration"))
                .andExpect(status().isFound()).andReturn();
        var params = UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl())
                .build().getQueryParams();
        assertThat(params.getFirst("client_id")).isEqualTo("WEB_CLIENT_PKCE");
        assertThat(params.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(params.getFirst("code_challenge")).isNotBlank();
        assertThat(params.getFirst("state")).isNotBlank();
        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/login/oauth2/code/web-client-registration")
                        .session(session).param("code", "invalid").param("state", "wrong"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:8083/?e=login_failed"));
    }

    @Test
    void publicStatusRoleAndLogoutUseFilterChain() throws Exception {
        mvc.perform(get("/api/auth/status")).andExpect(status().isOk())
                .andExpect(content().json("{\"isLoggedIn\":false}"));
        mvc.perform(get("/api/role/guest")).andExpect(status().isUnauthorized());
        var logout = mvc.perform(get("/api/auth/logout")).andExpect(status().isFound()).andReturn();
        assertThat(logout.getResponse().getRedirectedUrl()).startsWith("http://localhost:9000/connect/logout?");
        assertThat(logout.getResponse().getHeader("Set-Cookie"))
                .contains("__Host-SESSION=").contains("Max-Age=0").contains("Secure").contains("HttpOnly");
    }

    @Test
    void backChannelRejectsMissingLogoutToken() throws Exception {
        mvc.perform(post("/logout/connect/back-channel/web-client-registration"))
                .andExpect(status().is4xxClientError());
    }

    @TestConfiguration
    static class RegistrationConfig {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("web-client-registration")
                    .clientId("WEB_CLIENT_PKCE").clientSecret("secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "WEB_CLIENT_READ")
                    .authorizationUri("http://localhost:9000/oauth2/authorize")
                    .tokenUri("http://localhost:9000/oauth2/token")
                    .jwkSetUri("http://localhost:9000/oauth2/jwks")
                    .issuerUri("http://localhost:9000")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
