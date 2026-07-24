package com.roots.authserver.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

@Configuration
public class GoogleAuthConfig {

    /**
     * Verifies Google Sign-In id_tokens: signature against Google's published keys
     * (fetched and cached by the library), issuer, expiry, and audience — which must be
     * our own Google OAuth client id, so tokens minted for other apps are rejected.
     */
    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(@Value("${google.client-id}") String googleClientId) {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(googleClientId))
                .build();
    }

    /**
     * No baseUrl: used only for the server-to-server authorization-code exchange
     * against Google's fixed token endpoint (SocialLoginService supplies the full URI).
     */
    @Bean
    public RestClient restClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder.build();
    }
}
