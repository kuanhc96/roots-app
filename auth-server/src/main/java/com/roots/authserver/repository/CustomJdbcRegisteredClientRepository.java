package com.roots.authserver.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import tools.jackson.databind.json.JsonMapper;

public class CustomJdbcRegisteredClientRepository extends JdbcRegisteredClientRepository {
    private final JsonMapper jsonMapper = new JsonMapper();
    /**
     * Constructs a {@code JdbcRegisteredClientRepository} using the provided parameters.
     *
     * @param jdbcOperations the JDBC operations
     */
    public CustomJdbcRegisteredClientRepository(JdbcOperations jdbcOperations) {
        super(jdbcOperations);
    }

    public List<RegisteredClient> findAll() {
        return getJdbcOperations().query("SELECT * FROM oauth2_registered_client", (rs, rowNum) -> {
            String id = rs.getString("id");
            String clientId = rs.getString("client_id");
            String clientSecret = rs.getString("client_secret");
            
            // Parse clientAuthenticationMethods JSON to ClientAuthenticationMethod objects
            @SuppressWarnings("unchecked")
            Set<String> clientAuthMethodStrings = jsonMapper.readValue(rs.getString("client_authentication_methods"), Set.class);
            Set<ClientAuthenticationMethod> clientAuthenticationMethods = clientAuthMethodStrings.stream()
                    .map(ClientAuthenticationMethod::new)
                    .collect(Collectors.toUnmodifiableSet());
            
            // Parse authorizationGrantTypes JSON to AuthorizationGrantType objects
            @SuppressWarnings("unchecked")
            Set<String> grantTypeStrings = jsonMapper.readValue(rs.getString("authorization_grant_types"), Set.class);
            Set<AuthorizationGrantType> authorizationGrantTypes = grantTypeStrings.stream()
                    .map(grant -> new AuthorizationGrantType(grant))
                    .collect(Collectors.toUnmodifiableSet());
            
            // Parse redirectUris JSON to Set<String>
            @SuppressWarnings("unchecked")
            Set<String> redirectUris = jsonMapper.readValue(rs.getString("redirect_uris"), Set.class);
            
            // Parse scopes JSON to Set<String>
            @SuppressWarnings("unchecked")
            Set<String> scopes = jsonMapper.readValue(rs.getString("scopes"), Set.class);
            
            // Parse clientSettings JSON to Map
            @SuppressWarnings("unchecked")
            Map<String, Object> clientSettingsMap = jsonMapper.readValue(rs.getString("client_settings"), Map.class);
            
            // Parse tokenSettings JSON to Map, then build TokenSettings from it
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenSettingsMap = jsonMapper.readValue(rs.getString("token_settings"), Map.class);

            return RegisteredClient.withId(id)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .clientAuthenticationMethods(methods -> methods.addAll(clientAuthenticationMethods))
                    .authorizationGrantTypes(grants -> grants.addAll(authorizationGrantTypes))
                    .redirectUris(uris -> uris.addAll(redirectUris))
                    .scopes(scopesSet -> scopesSet.addAll(scopes))
                    .clientSettings(ClientSettings.withSettings(clientSettingsMap).build())
                    .tokenSettings(TokenSettings.withSettings(tokenSettingsMap).build())
                    .build();
        });
    }
}
