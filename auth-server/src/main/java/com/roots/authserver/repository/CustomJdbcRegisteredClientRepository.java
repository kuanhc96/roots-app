package com.roots.authserver.repository;

import java.util.Arrays;
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
import org.springframework.stereotype.Repository;

import tools.jackson.databind.json.JsonMapper;

@Repository
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

            // Parse clientSettings JSON to Map
            @SuppressWarnings("unchecked")
            Map<String, Object> clientSettingsMap = jsonMapper.readValue(rs.getString("client_settings"), Map.class);

            // Parse tokenSettings JSON to Map, then build TokenSettings from it
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenSettingsMap = jsonMapper.readValue(rs.getString("token_settings"), Map.class);

            // Parse clientAuthenticationMethods: comma-separated values to ClientAuthenticationMethod objects
            Set<ClientAuthenticationMethod> clientAuthenticationMethods = parseStringSet(rs.getString("client_authentication_methods")).stream()
                    .map(ClientAuthenticationMethod::new)
                    .collect(Collectors.toUnmodifiableSet());

            // Parse authorizationGrantTypes: comma-separated values to AuthorizationGrantType objects
            Set<AuthorizationGrantType> authorizationGrantTypes = parseStringSet(rs.getString("authorization_grant_types")).stream()
                    .map(AuthorizationGrantType::new)
                    .collect(Collectors.toUnmodifiableSet());
            
            // Parse redirectUris: comma-separated values to Set<String>
            Set<String> redirectUris = parseStringSet(rs.getString("redirect_uris"));

            // Parse scopes: comma-separated values to Set<String>
            Set<String> scopes = parseStringSet(rs.getString("scopes"));
            
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

    /**
     * Parses a comma-separated string into a Set of trimmed strings.
     * Handles null/empty strings gracefully.
     *
     * @param commaSeparatedValues the comma-separated string (e.g., "A,B,C" or "A")
     * @return an unmodifiable Set of parsed values, or an empty Set if input is null/blank
     */
    private Set<String> parseStringSet(String commaSeparatedValues) {
        if (commaSeparatedValues == null || commaSeparatedValues.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(commaSeparatedValues.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
