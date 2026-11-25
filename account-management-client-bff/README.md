# account-management-client-bff

A full-stack BFF combining a copy of `account-management-client` with
`account-management-bff`. Like `web-client-bff`, Maven generates an embedded Nuxt
SPA and Spring Boot serves it alongside the same-origin BFF API. The default HTTP
port is **8084**, matching the original account-management BFF.

The original projects remain unchanged and independently usable.
`account-management` remains a separate resource service. Compose starts this
service after `gateway-server` and `auth-server`; its CI/CD
workflows build and publish the `account-management-client-bff` image.
Compose publishes it at `localhost:8084`; the legacy `account-management-bff`
remains an internal gateway dependency and does not publish its host port.

## Structure

```text
account-management-client-bff/
  frontend/
    app.vue
    composables/useOAuth.ts
    pages/index.vue
    pages/logout.vue
    nuxt.config.ts
  src/main/java/com/roots/account_management_client_bff/
    component/
    config/
    controller/
    dto/
    service/
  src/main/resources/application.yaml
  pom.xml
```

The SPA uses `ssr: false`. Maven installs Node/npm under `target`, runs
`npm install` and `npm run generate` in `frontend`, and copies
`frontend/.output/public` to `target/classes/static`. `SpaFallbackConfig` serves
static assets and falls back to `index.html` for client-side routes like `/logout`.
No generated assets are written into `src`.

## Authentication

The browser calls `/api/auth/status` and `/api/auth/logout` directly on this
service, not through the gateway. Login starts with a full navigation to
`/oauth2/authorization/account-management-client-registration`. Spring Security's
servlet OAuth2 Login support owns the authorization-code exchange and the default
callback `/login/oauth2/code/account-management-client-registration`, including
state, S256 PKCE (also enabled for this confidential client), nonce, and OIDC
ID-token validation. The old `/api/auth/authorize` and `/api/auth/callback`
controller endpoints are removed.

Spring Security stores access and refresh tokens in
`InMemoryOAuth2AuthorizedClientService`, keyed by registration ID and principal
name. The authenticated `OidcUser`, including the validated ID token, lives in the
ordinary servlet HTTP session. This BFF has no Redis connection or Spring Session
dependency. The browser holds only the secure, HttpOnly, SameSite=Lax
`__Host-AMC_SESSION` cookie and locally cached login claims. Container session
tracking is cookie-only; session IDs are not rewritten into URLs.

Login always redirects to the configured SPA origin's `/`. OAuth/OIDC failures
redirect to `/?e=login_failed` on that same origin. Saved-request redirects are
not enabled. The SPA `/` is also the configured login page, disabling Spring
Security's generated login/logout pages so `/logout` remains the SPA landing page.
`/api/auth/status` reports authentication from the session and
returns the same identity-claims response without exchanging tokens. ID-token
expiry does not end the session. A servlet authorized-client manager supports
refresh-on-demand for future outbound API calls; status does not invoke it.
Access-token refresh does not replace the session's ID token.

All state is process-local and lost on restart; this is a single-instance design.
Two sessions for the same principal share one authorized-client entry. Logging out
one removes that shared entry, even if the other session remains authenticated.

Provider discovery uses `AUTH_SERVER_ISSUER`, which must be reachable from the
BFF at startup and exactly match the ID token's `iss`. Token and JWK requests use
the internal auth-server URL; the browser authorization redirect uses its external
URL. Auth-server supports an optional fixed `AUTH_SERVER_ISSUER`. Compose sets it
to `http://auth-server:9000`, matching this BFF's internal discovery URL and keeping
ID-token and logout-token issuers consistent. Local runs without a fixed issuer
continue to infer the issuer from the incoming request URL.

GET `/api/auth/logout` is handled by Spring Security's logout filter. It removes
the authorized client, invalidates the session, clears the cookie/security context,
and redirects to auth-server's `/connect/logout`. The logout success handler uses
the original authentication's `OidcUser` ID token for `id_token_hint` even though
the HTTP session has already been invalidated. The existing GET contract is
retained; CSRF remains disabled and a CSRF-protected POST would be preferable
for production.

Back-channel logout uses Spring Security's form-encoded POST
`/logout/connect/back-channel/account-management-client-registration`
(`logout_token` parameter), not the removed `/api/auth/logout/back-channel/test`.
Spring Security validates the signed token and matches sessions through
`InMemoryOidcSessionRegistry`. Its internal per-session logout requests use the
custom `__Host-AMC_SESSION` cookie and invoke the same authorized-client cleanup
and session-invalidation handlers. Session events remove stale registry entries.
The default implementation has no separate `jti` replay cache; successful logout
removes matching session mappings, and unmatched notifications are idempotent.
Use provider-issued session-specific (`sid`) notifications.

This browser-facing app has no Eureka dependency or registration and needs no
inbound gateway route.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8084` | HTTP listen port |
| `ACCOUNT_MANAGEMENT_CLIENT_BFF_EXTERNAL_LOCATION` | `http://localhost:8084` | Default SPA origin |
| `ACCOUNT_MANAGEMENT_CLIENT_ORIGIN` | Public app URL above | Post-login and post-logout SPA origin |
| `AUTH_SERVER_INTERNAL_LOCATION` | `http://localhost:9000` | Server-to-server token exchanges and JWK retrieval |
| `AUTH_SERVER_EXTERNAL_LOCATION` | `http://localhost:9000` | Browser authorization and logout redirects |
| `AUTH_SERVER_ISSUER` | Internal auth-server URL above | Expected ID-token issuer and startup discovery URL |
| `ACCOUNT_MANAGEMENT_CLIENT_ID` | `ACCOUNT_MANAGEMENT_CLIENT` | Confidential PKCE OAuth client |
| `ACCOUNT_MANAGEMENT_CLIENT_SECRET` | Required; no default | Must match auth-server's registered client secret |

The callback uses Spring Security's `{baseUrl}/login/oauth2/code/{registrationId}`
template, derived from the incoming request. Register the matching callback/logout
URLs in auth-server and configure trusted forwarded headers when deploying behind
a reverse proxy. Set the SPA origin separately if it differs from the default.

## Running

Start auth-server (which still needs its own DB and Redis). This BFF does not
need the shared `bff-server-redis`.
Then, in PowerShell:

```powershell
Set-Location account-management-client-bff
$env:ACCOUNT_MANAGEMENT_CLIENT_SECRET = 'secret'
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8084/`. Stop the original `account-management-bff` first:
both default to port 8084 and use the same session cookie. Do not run them as
independent browser sessions on the same host.

Build the executable JAR, including its frontend:

```powershell
.\mvnw.cmd package
```

## OAuth client seed

`auth-server/src/main/resources/initialize_db/create_client_table.sql` registers
`http://localhost:8084/login/oauth2/code/account-management-client-registration`
for `ACCOUNT_MANAGEMENT_CLIENT`, retaining its legacy `/api/auth/callback` URI
for the separate legacy BFF. `ACCOUNT_MANAGEMENT_SUPER_CLIENT` is not migrated
and retains `/api/auth/callback`. Both permit `http://localhost:8084/logout` while
retaining `http://localhost:3001/logout` for the standalone client.
Only `ACCOUNT_MANAGEMENT_CLIENT` enables back-channel logout, targeting
`http://account-management-client-bff:8084/logout/connect/back-channel/account-management-client-registration`
for Compose. The URI must be reachable from auth-server. For host-run services,
change the hostname to `localhost` using the update below.

Initialization scripts only run for a fresh database. For an existing database,
apply this targeted update rather than rerunning the destructive seed script:

```sql
UPDATE oauth2_registered_client
SET redirect_uris = CONCAT_WS(',', NULLIF(redirect_uris, ''),
    'http://localhost:8084/login/oauth2/code/account-management-client-registration')
WHERE client_id = 'ACCOUNT_MANAGEMENT_CLIENT'
  AND (redirect_uris IS NULL OR redirect_uris NOT LIKE
    '%http://localhost:8084/login/oauth2/code/account-management-client-registration%');
```

For an existing database, also enable the back-channel registration without
replacing the other client settings:

```sql
UPDATE oauth2_registered_client
SET client_settings = JSON_SET(
    client_settings,
    '$."settings.client.back-channel-logout-session-required"', TRUE,
    '$."settings.client.back-channel-logout-uri"',
    'http://localhost:8084/logout/connect/back-channel/account-management-client-registration')
WHERE client_id = 'ACCOUNT_MANAGEMENT_CLIENT';
```

For Compose, substitute `account-management-client-bff` for `localhost` in that
back-channel URL. Existing databases require both registration updates; editing
the seed does not update existing rows. Restart/rebuild auth-server with the fixed
issuer configuration before using the migrated Docker deployment.
