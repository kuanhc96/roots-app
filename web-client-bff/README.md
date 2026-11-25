# web-client-bff

The full-stack BFF for `web-client` embeds a Nuxt SPA and serves it alongside
same-origin authentication and role APIs on port **8083**. The standalone
`web-client/` remains independent. Maven generates the SPA under `frontend/`
and copies it into the application's static resources; `SpaFallbackConfig`
serves assets and falls back to the SPA shell for client-side routes.

## Authentication and sessions

The browser starts login by navigating to
`/oauth2/authorization/web-client-registration`. Spring Security OAuth2 Login
performs the authorization-code flow using the confidential `WEB_CLIENT_PKCE`
client, `client_secret_basic`, OIDC, and S256 PKCE. Spring manages the state,
nonce, verifier and code exchange, validates the ID token, and receives the
callback at `/login/oauth2/code/web-client-registration`. The former custom
`/api/auth/authorize` and `/api/auth/callback` endpoints are not used.

`GET /api/auth/status` reports the authenticated session's `OidcUser` identity
as `isLoggedIn`, `email`, `userGUID`, and `roles`. A logged-out response is
`{"isLoggedIn":false}`; a guest has no `userGUID`. Status does not refresh
tokens, and an expired ID token does not itself end the servlet session.

The browser holds the secure, HttpOnly, SameSite=Lax `__Host-SESSION` cookie;
tokens remain server-side. The validated OIDC identity and ID token live in an
ordinary in-memory servlet session, and access/refresh tokens live in Spring
Security's in-memory authorized-client service. There is **no Redis or Spring
Session connection** in this BFF. Restarting loses sessions and tokens;
multiple instances do not share them. Authorized clients are keyed by
registration and principal, so sessions for the same principal share an entry:
logging out one can remove the authorized client used by another.

`GET /api/auth/logout` triggers Spring Security logout: it removes the authorized
client, invalidates the session and redirects to auth-server's OIDC logout
endpoint with the SPA logout return URL and an ID-token hint when available. CSRF is disabled;
a CSRF-protected POST would be preferable for production. Auth-server may also
send a form-encoded `logout_token` to
`POST /logout/connect/back-channel/web-client-registration`. Spring validates
the token and matches the OIDC session before invalidating it. The registered
back-channel URL must be reachable **from auth-server**, not just the browser.

## Role proxy

`GET /api/role/{role}` supports `pastor`, `deacon`, `small-group-leader`,
`vice-small-group-leader`, `member`, and `guest`. The BFF obtains the
authorized client's access token, refreshing it when necessary, and forwards
the request through the gateway at
`{GATEWAY_SERVER_INTERNAL_LOCATION}/roots-app/simple-resource-server/role/{role}`
with a bearer token. No usable authorization returns 401; downstream 2xx/4xx
responses are relayed, and server or connection failures return 502. The
browser does not receive tokens. This service is browser-facing, does not
register with Eureka, and has no inbound gateway route.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8083` | HTTP listen port |
| `BFF_SERVER_EXTERNAL_LOCATION` | `http://localhost:8083` | Default SPA origin for login/logout redirects |
| `WEB_CLIENT_ORIGIN` | `BFF_SERVER_EXTERNAL_LOCATION` | Overrides the SPA origin for login/logout redirects |
| `AUTH_SERVER_INTERNAL_LOCATION` | `http://localhost:9000` | Server-to-server token and JWK requests |
| `AUTH_SERVER_EXTERNAL_LOCATION` | `http://localhost:9000` | Browser-facing authorization and logout URL |
| `AUTH_SERVER_ISSUER` | Internal auth-server URL | Discovery URL and expected ID-token issuer |
| `WEB_CLIENT_ID` | `WEB_CLIENT_PKCE` | Registered OAuth client |
| `WEB_CLIENT_SECRET` | Required | Must match the registered client secret |
| `GATEWAY_SERVER_INTERNAL_LOCATION` | `http://localhost:8080` | Gateway URL for outbound role calls |

`AUTH_SERVER_ISSUER` must be reachable by the BFF for discovery and exactly
match the issuer in auth-server's tokens. Compose configures both services to
use `http://auth-server:9000` as issuer and uses that internal hostname for
token and JWK requests; browser redirects use `http://localhost:9000` by
default. For host-run services, use an issuer reachable on the host and ensure
auth-server issues tokens with exactly the same issuer. The callback URI uses
Spring's `{baseUrl}/login/oauth2/code/{registrationId}` template: register the
public URI and configure trusted forwarded headers when behind a reverse proxy.

## OAuth registration and existing databases

The seed in `auth-server/src/main/resources/initialize_db/create_client_table.sql`
adds `http://localhost:8083/login/oauth2/code/web-client-registration` to
`WEB_CLIENT_PKCE` without removing the existing callbacks. It registers
`http://web-client-bff:8083/logout/connect/back-channel/web-client-registration`
for back-channel logout inside Compose, and keeps
`http://localhost:8083/logout` as a post-logout URI.

MySQL initialization scripts run only on a fresh database. For an existing
database, apply these targeted updates rather than rerunning the destructive
table-creation script:

```sql
UPDATE oauth2_registered_client
SET redirect_uris = CONCAT_WS(',', NULLIF(redirect_uris, ''),
    'http://localhost:8083/login/oauth2/code/web-client-registration')
WHERE client_id = 'WEB_CLIENT_PKCE'
  AND (redirect_uris IS NULL OR FIND_IN_SET(
    'http://localhost:8083/login/oauth2/code/web-client-registration', redirect_uris) = 0);

UPDATE oauth2_registered_client
SET post_logout_redirect_uris = CONCAT_WS(',', NULLIF(post_logout_redirect_uris, ''),
    'http://localhost:8083/logout')
WHERE client_id = 'WEB_CLIENT_PKCE'
  AND (post_logout_redirect_uris IS NULL OR FIND_IN_SET(
    'http://localhost:8083/logout', post_logout_redirect_uris) = 0);

UPDATE oauth2_registered_client
SET client_settings = JSON_SET(
    client_settings,
    '$."settings.client.back-channel-logout-session-required"', CAST('true' AS JSON),
    '$."settings.client.back-channel-logout-uri"',
    'http://web-client-bff:8083/logout/connect/back-channel/web-client-registration')
WHERE client_id = 'WEB_CLIENT_PKCE';
```

For host-run auth-server and BFF, replace `web-client-bff` with `localhost` in
the back-channel URL. For other deployments use an address reachable from the
authorization server, and register the actual public callback and logout URIs.
The seed's `{noop}secret` is for development only.

## Running and testing

Start auth-server and its own database and Redis, and start gateway-server and
its downstream resource service for role calls. This BFF itself needs no Redis.
For example, from this directory in PowerShell:

```powershell
$env:WEB_CLIENT_SECRET = 'secret'
mvn spring-boot:run
```

Open `http://localhost:8083/`. Alternatively, set the Compose environment
variables and run `docker compose up -d --wait web-client-bff` from the
repository root. The BFF directly waits for auth-server and gateway-server;
neither the BFF nor the gateway connects to the legacy shared Redis instance.

CI builds the frontend and JAR, runs unit tests, builds the local BFF image,
starts Compose, waits for the gateway resource route, and runs the live
integration tests:

```powershell
mvn surefire:test '-Dtest=%regex[.*integration.*]'
```

Those tests require the running services and a seeded client registration.
CI uses published dependency images, supplies `WEB_CLIENT_SECRET=secret`,
and requires Docker Hub, MySQL, mail, and Google client secrets. The auth-server
mail health check requires working SMTP credentials. CD builds the SPA and
publishes versioned and `latest` images; see the workflows for details.
