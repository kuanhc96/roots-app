DROP TABLE IF EXISTS oauth2_registered_client;

CREATE TABLE oauth2_registered_client (
    id                            VARCHAR(100)  NOT NULL,
    client_id                     VARCHAR(100)  NOT NULL,
    client_id_issued_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret                 VARCHAR(200)  DEFAULT NULL,
    client_secret_expires_at      TIMESTAMP     DEFAULT NULL,
    client_name                   VARCHAR(200)  NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types     VARCHAR(1000) NOT NULL,
    redirect_uris                 VARCHAR(1000) DEFAULT NULL,
    post_logout_redirect_uris     VARCHAR(1000) DEFAULT NULL,
    scopes                        VARCHAR(1000) NOT NULL,
    client_settings               VARCHAR(2000) NOT NULL,
    token_settings                VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id)
);

-- Update client_secret to match the value used for NUXT_PUBLIC_WEB_CLIENT_SECRET in web-client.
INSERT INTO oauth2_registered_client (
    id,
    client_id,
    client_id_issued_at,
    client_secret,
    client_secret_expires_at,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    post_logout_redirect_uris,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'web-client-00000000-0000-0000-0000-000000000001',
    'WEB_CLIENT',
    CURRENT_TIMESTAMP,
    '{noop}secret',
    NULL,
    'WEB_CLIENT',
    'client_secret_basic',
    'refresh_token,authorization_code',
    -- Two registered callbacks during the bff migration: web-client's own (legacy
    -- browser-side exchange, removed once web-client is repointed) and bff-server's
    -- (the server-side exchange at GET /api/auth/callback).
    'http://localhost:3000/callback,http://localhost:8083/api/auth/callback',
    'http://localhost:3000/logout',
    'openid,WEB_CLIENT_READ',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    -- reuse-refresh-tokens=false: every refresh exchange rotates the refresh token, so
    -- bff-server always stores a fresh one with a fresh TTL (see bff-server's auth status flow).
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",300.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}'
);

-- Machine-to-machine client used by integration tests; authenticates via the
-- client_credentials flow. No redirect/logout URIs (no user, no browser).
INSERT INTO oauth2_registered_client (
    id,
    client_id,
    client_id_issued_at,
    client_secret,
    client_secret_expires_at,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    post_logout_redirect_uris,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'integration-test-client-00000000-0000-0000-0000-000000000002',
    'INTEGRATION_TEST_CLIENT',
    CURRENT_TIMESTAMP,
    '{noop}integration-test-secret',
    NULL,
    'INTEGRATION_TEST_CLIENT',
    'client_secret_basic',
    'client_credentials',
    NULL,
    NULL,
    'INTEGRATION_TEST_CLIENT_READ,INTEGRATION_TEST_CLIENT_WRITE,INTEGRATION_TEST_CLIENT_UPDATE,INTEGRATION_TEST_CLIENT_DELETE',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",300.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}'
);
