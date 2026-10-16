# account-management-client-bff

A full-stack BFF combining a copy of `account-management-client` with
`account-management-bff`. Like `web-client-bff`, Maven generates an embedded Nuxt
SPA and Spring Boot serves it alongside the same-origin BFF API. The default HTTP
port is **8084**, matching the original account-management BFF.

The original projects remain unchanged and independently usable.
`account-management` remains a separate resource service. Compose and CI/CD
integration are intentionally unchanged; run this service directly for now.

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
    client/
    config/
    controller/
    dto/
    enums/
    service/
    util/
  src/main/resources/application.yaml
  pom.xml
```

The SPA uses `ssr: false`. Maven installs Node/npm under `target`, runs
`npm install` and `npm run generate` in `frontend`, and copies
`frontend/.output/public` to `target/classes/static`. `SpaFallbackConfig` serves
static assets and falls back to `index.html` for client-side routes like `/logout`.
No generated assets are written into `src`.

## Authentication

The browser calls `/api/auth/status`, `/api/auth/authorize`, and `/api/auth/logout`
directly on this service, not through the gateway. The BFF owns
`/api/auth/callback`, validates state, PKCE and nonce, and redirects to the embedded
SPA. Tokens stay in Redis; the browser holds only the secure, HttpOnly,
SameSite=Lax `__Host-AMC_SESSION` cookie and locally cached login claims.

The existing status/refresh and RP-initiated OIDC logout flows are preserved.
Back-channel logout is not implemented. This browser-facing app has no Eureka
dependency or registration and needs no inbound gateway route.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8084` | HTTP listen port |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Spring Session and token storage |
| `ACCOUNT_MANAGEMENT_CLIENT_BFF_EXTERNAL_LOCATION` | `http://localhost:8084` | Public app URL; builds `/api/auth/callback` |
| `ACCOUNT_MANAGEMENT_CLIENT_ORIGIN` | Public app URL above | Post-login and post-logout SPA origin |
| `AUTH_SERVER_INTERNAL_LOCATION` | `http://localhost:9000` | Server-to-server token exchanges |
| `AUTH_SERVER_EXTERNAL_LOCATION` | `http://localhost:9000` | Browser authorization and logout redirects |
| `ACCOUNT_MANAGEMENT_CLIENT_ID` | `ACCOUNT_MANAGEMENT_CLIENT` | Confidential PKCE OAuth client |
| `ACCOUNT_MANAGEMENT_CLIENT_SECRET` | Required; no default | Must match auth-server's registered client secret |
| `REFRESH_TOKEN_TTL_SECONDS` | `3600` | Refresh-token Redis TTL |

Changing the listen port alone does not change OAuth URLs. Set the external
location too and register matching callback/logout URLs in auth-server.

## Running

Start auth-server (with its DB and Redis) and the shared `bff-server-redis`.
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
`http://localhost:8084/api/auth/callback` for both `ACCOUNT_MANAGEMENT_CLIENT` and
`ACCOUNT_MANAGEMENT_SUPER_CLIENT`. Both permit `http://localhost:8084/logout` while
retaining `http://localhost:3001/logout` for the standalone client.

Initialization scripts only run for a fresh database. For an existing database,
apply this targeted update rather than rerunning the destructive seed script:

```sql
UPDATE oauth2_registered_client
SET redirect_uris = 'http://localhost:8084/api/auth/callback',
    post_logout_redirect_uris =
        CONCAT_WS(',', NULLIF(post_logout_redirect_uris, ''), 'http://localhost:8084/logout')
WHERE client_id IN ('ACCOUNT_MANAGEMENT_CLIENT', 'ACCOUNT_MANAGEMENT_SUPER_CLIENT')
  AND (post_logout_redirect_uris IS NULL
       OR post_logout_redirect_uris NOT LIKE '%http://localhost:8084/logout%');
```
