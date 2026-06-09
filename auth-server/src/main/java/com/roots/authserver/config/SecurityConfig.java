package com.roots.authserver.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.roots.authserver.component.GuestAuthenticationProvider;
import com.roots.authserver.component.MfaAwareDaoAuthenticationProvider;
import com.roots.authserver.component.MfaAwareRememberMeAuthenticationProvider;
import com.roots.authserver.component.MfaRedirectAuthenticationSuccessHandler;
import com.roots.authserver.component.RememberMeOidcLogoutAuthenticationSuccessHandler;
import com.roots.authserver.service.UserCredentialService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import com.roots.authserver.service.InMemoryOneTimePinService;

import org.springframework.security.authentication.ott.JdbcOneTimeTokenService;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import javax.sql.DataSource;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${remember-me.key}")
    private String rememberMeKey;

    @Value("${remember-me.token-validity-seconds}")
    private int rememberMeTokenValiditySeconds;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .oauth2AuthorizationServer((authorizationServer) -> {
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    authorizationServer.oidc(oidc -> oidc.logoutEndpoint(logout -> logout.logoutResponseHandler(rememberMeOidcLogoutAuthenticationSuccessHandler())));
                })
                .authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            MfaRedirectAuthenticationSuccessHandler successHandler,
            TokenBasedRememberMeServices rememberMeServices,
            RememberMeAuthenticationFilter rememberMeAuthenticationFilter) throws Exception {
        http
                .authenticationManager(authenticationManager)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .rememberMe(rm -> rm.rememberMeServices(rememberMeServices))
                .addFilterAfter(rememberMeAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .successHandler(successHandler)
                )
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    // TODO: this is a temporary measure. The frontend client should not be able to
    // call the auth-server's /token endpoint directly
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(DataSource dataSource) {
        return new JdbcRegisteredClientRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    public UserDetailsService userDetailsService(DataSource dataSource) {
        JdbcUserDetailsManager manager = new JdbcUserDetailsManager(dataSource);
        manager.setUsersByUsernameQuery(
                "SELECT email, password, true FROM user_credential WHERE email = ?"
        );
        manager.setAuthoritiesByUsernameQuery(
                "SELECT uc.email, r.role_name " +
                "FROM user_credential uc JOIN role r ON r.credential_id = uc.id " +
                "WHERE uc.email = ?"
        );
        return manager;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UsernamePasswordAuthenticationFilter usernamePasswordAuthenticationFilter(AuthenticationManager authenticationManager, RememberMeServices rememberMeServices) {
        UsernamePasswordAuthenticationFilter usernamePasswordAuthenticationFilter = new UsernamePasswordAuthenticationFilter(authenticationManager);
        usernamePasswordAuthenticationFilter.setRememberMeServices(rememberMeServices);
        return usernamePasswordAuthenticationFilter;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            MfaAwareDaoAuthenticationProvider authenticationProvider,
            RememberMeAuthenticationProvider rememberMeAuthenticationProvider,
            GuestAuthenticationProvider guestAuthenticationProvider) {
        return new ProviderManager(authenticationProvider, rememberMeAuthenticationProvider, guestAuthenticationProvider);
    }

    @Bean
    public RememberMeAuthenticationProvider rememberMeAuthenticationProvider(UserDetailsService userDetailsService, UserCredentialService userCredentialService) {
        return new MfaAwareRememberMeAuthenticationProvider(rememberMeKey, userDetailsService, userCredentialService);
    }

    @Bean
    public RememberMeAuthenticationFilter rememberMeAuthenticationFilter(
            AuthenticationManager authenticationManager,
            TokenBasedRememberMeServices rememberMeServices,
            MfaRedirectAuthenticationSuccessHandler successHandler
    ) {
        RememberMeAuthenticationFilter rememberMeAuthenticationFilter = new RememberMeAuthenticationFilter(authenticationManager, rememberMeServices);
        rememberMeAuthenticationFilter.setAuthenticationSuccessHandler(successHandler);
        return rememberMeAuthenticationFilter;
    }

    @Bean
    public TokenBasedRememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
        TokenBasedRememberMeServices services = new TokenBasedRememberMeServices(
                rememberMeKey, userDetailsService, RememberMeTokenAlgorithm.SHA256);
        services.setAlwaysRemember(false);
        services.setTokenValiditySeconds(rememberMeTokenValiditySeconds);
        return services;
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                Authentication principal = context.getPrincipal();
                Set<String> roles = principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(String::toUpperCase)
                        .collect(Collectors.toSet());
                context.getClaims().claim("roles", roles);
            }
        };
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();

        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .build();
    }

    @Bean
    public InMemoryOneTimePinService inMemoryOneTimePinService() {
        return new InMemoryOneTimePinService();
    }

    @Bean
    public JdbcOneTimeTokenService jdbcOneTimeTokenService(DataSource dataSource) {
        return new JdbcOneTimeTokenService(new JdbcTemplate(dataSource));
    }

    @Bean
    public RememberMeOidcLogoutAuthenticationSuccessHandler rememberMeOidcLogoutAuthenticationSuccessHandler() {
        return new RememberMeOidcLogoutAuthenticationSuccessHandler(new OidcLogoutAuthenticationSuccessHandler());
    }
}
