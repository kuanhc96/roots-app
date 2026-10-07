# web-client-bff

The **full-stack backend-for-frontend** for `web-client`. It embeds a copy of the Nuxt 4 frontend and serves its generated SPA from Spring Boot, following the same static-SPA architecture as `auth-server`. OAuth2 tokens stay server-side in Redis; the browser holds only the `__Host-SESSION` cookie and the UI stores returned login claims.

`web-client/` remains an independent standalone frontend. The embedded SPA calls web-client-bff directly at same-origin `/api/auth/**` and `/api/role/**` endpoints; role calls are proxied by the bff to simple-resource-server (see [Role proxy](#role-proxy--get-apirole)).

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `SERVER_PORT` | No | `8083` | HTTP port the server listens on |
| `REDIS_HOST` | No | `localhost` | Redis host backing Spring Session + the token store |
| `REDIS_PORT` | No | `6379` | Redis port |
| `EUREKA_SERVER_URL` | No | `http://localhost:8070/eureka/` | Eureka registry URL; compose sets `http://eureka-server:8070/eureka/` |
| `WEB_CLIENT_ORIGIN` | No | `http://localhost:8083` | Public origin of this app; used for OAuth callback exchanges and the post-logout return URL (property `web.client.origin`) |
| `AUTH_SERVER_INTERNAL_LOCATION` | No | `http://localhost:9000` | Auth-server base URL reachable from **inside** the deployment network; used by the server-to-server RestClient (refresh-token exchange). Property `auth-server.internal-location`; docker-compose sets `http://auth-server:9000` |
| `AUTH_SERVER_EXTERNAL_LOCATION` | No | `http://localhost:9000` | Auth-server base URL reachable from **outside** — i.e. by the user's browser; used in redirects the browser follows (the authorize kick-off). Property `auth-server.external-location`. Separate from the internal one because in docker `auth-server:9000` doesn't resolve outside the compose network — compose leaves this at the default |
| `WEB_CLIENT_ID` | No | `WEB_CLIENT_PKCE` | OAuth2 client id the bff uses for authorize/token exchanges (property `web.client.id`) |
| `WEB_CLIENT_SECRET` | **Yes** | — (no default) | Client secret for `WEB_CLIENT_PKCE` confidential PKCE flow (`client_secret_basic`) |
| `REFRESH_TOKEN_TTL_SECONDS` | No | `3600` | Redis TTL applied to stored refresh tokens (property `token-store.refresh-token-ttl-seconds`); mirrors auth-server's `refresh-token-time-to-live` |
| `GATEWAY_SERVER_INTERNAL_LOCATION` | No | `http://localhost:8080` | gateway-server base URL reachable from inside the deployment network; the role proxy calls `{this}/roots-app/simple-resource-server/role/*` (property `gateway-server.internal-location`) |

## Role proxy — `GET /api/role/*`

`RoleProxyController` exposes six GET endpoints mirroring simple-resource-server 1:1: `/api/role/pastor`, `/deacon`, `/small-group-leader`, `/vice-small-group-leader`, `/member`, `/guest`. The SPA calls them same-origin with only its `__Host-SESSION` cookie (`RoleProxyController` → `RoleProxyService` → `SimpleResourceClient`):

1. `<sessionId>:access_token` present → used as-is.
2. Otherwise `TokenRefreshService` (shared with `/api/auth/status`) exchanges `<sessionId>:refresh_token` as `web.client.id`, stores the rotated tokens, and uses the new access token. A rejected refresh token is deleted.
3. No usable token → **401** without calling downstream (the SPA redirects to `/session-expired`).
4. The call goes to `{gateway-server.internal-location}/roots-app/simple-resource-server/role/<role>` with `Authorization: Bearer <access_token>` and **no** session cookie, so the gateway's cookie-driven token filters are no-ops (they would refresh as `WEB_CLIENT`, not this app's `WEB_CLIENT_PKCE`).
5. Downstream 2xx/4xx are relayed verbatim (e.g. **403** when the login lacks the role); a 5xx or connection failure becomes **502**.

## Eureka Service Discovery

web-client-bff is a Spring Cloud Netflix Eureka client. On startup, it automatically registers itself with the Eureka server (default: `http://localhost:8070/eureka/`), making itself discoverable by other services.

**De-registration:** To gracefully de-register from Eureka:
```bash
curl -X POST http://localhost:8083/actuator/shutdown
```

This triggers a clean shutdown with proper Eureka de-registration before the process exits.

## Login status — `GET /api/auth/status`

The endpoint web-client calls to ask "does this browser have a valid login?". Session-cookie driven, `permitAll`, and **always 200** — "not logged in" is a normal answer, not an error.

**Token store.** Each session's tokens live under three plain Redis string keys, each with its own TTL:

```
<sessionId>:access_token    TTL = the JWT's own exp
<sessionId>:refresh_token   TTL = REFRESH_TOKEN_TTL_SECONDS (opaque token, no readable exp)
<sessionId>:id_token        TTL = the JWT's own exp
```

`<sessionId>` is the Spring Session id — the base64-decoded value of the `__Host-SESSION` cookie. Because every key expires exactly when its token does, **an absent key is the expiry check**: reads never inspect `exp`.

**Flow** (`AuthController` → `AuthStatusService`):

1. `<sessionId>:id_token` present → logged in. Its payload is decoded (no signature verification — the bff itself stored it, and it originally came from auth-server over a server-to-server call) and returned as `{"isLoggedIn": true, "email": …, "userGUID": …, "roles": […]}`. A guest login has no `user_credential` row, so its response carries no `userGUID` field.
2. No id_token, but `<sessionId>:refresh_token` present → `AuthServerTokenClient` performs the `refresh_token` grant against `POST {auth-server.internal-location}/oauth2/token`, using the configured `web.client.id` (refresh tokens are bound to the issuing client). On success all three fresh tokens are stored and the id_token claims returned. auth-server seeds both web clients with `reuse-refresh-tokens=false`, so every exchange **rotates** the refresh token — the stored one is always new.
3. The exchange fails (expired, revoked, already-rotated, or garbage token → 400) → the stored refresh token is deleted (rotation means it can never succeed later) and the answer is `{"isLoggedIn": false}`.
4. Neither key → `{"isLoggedIn": false}` (claim fields omitted entirely — `NON_NULL` serialization).

The claims come from the **id_token**, which auth-server's `jwtTokenCustomizer` enriches with `email`, `userGUID`, and `roles` specifically for this endpoint.

## Authorize kick-off — `GET /api/auth/authorize`

Starts the OAuth2 authorization-code flow on behalf of web-client: an unconditional **302 browser redirect** to auth-server's `/oauth2/authorize` with every parameter filled in.

```
HTTP/1.1 302
Location: {auth-server.external-location}/oauth2/authorize
  ?response_type=code
  &client_id={web.client.id}
  &redirect_uri={bff-server.external-location}/api/auth/callback
  &scope=openid%20WEB_CLIENT_READ
  &state=<uuid>
  &code_challenge=<sha256(verifier)>
  &code_challenge_method=S256
```

- **state** and **PKCE code_verifier** are minted by the bff and stored at `<sessionId>:oauth_state` and `<sessionId>:oauth_code_verifier` (TTL 5 minutes); both are consumed one-time on callback.
- **No logged-in short-circuit**: if the auth-server session is already authenticated the flow completes silently without a login form; web-client should consult `/api/auth/status` first anyway.
- The Location uses `auth-server.external-location`, not `auth-server.internal-location` — the browser follows this redirect from outside the docker network, where the internal hostname doesn't resolve.

## Embedded SPA

Maven installs Node.js/npm, runs `npm install` and `npm run generate` under `frontend/`, then copies `frontend/.output/public` into Spring Boot's `classpath:/static/`. The Nuxt app uses `ssr: false`; `SpaFallbackConfig` serves real assets and falls back to `index.html` for client-side routes such as `/home`, `/logout`, and `/session-expired`.

Authentication calls use same-origin `/api/auth/status`, `/api/auth/authorize`, and `/api/auth/logout`; the server owns `/api/auth/callback`. Role API calls use same-origin `/api/role/*`, which the bff proxies to simple-resource-server — so no gateway CORS configuration is needed for the `http://localhost:8083` origin.

## Sessions in Redis (Spring Session)

HTTP sessions are stored in Redis via **Spring Session** (`spring-boot-starter-session-data-redis`), not in Tomcat memory:

- The servlet container's `JSESSIONID` tracking is disabled; the only session cookie is Spring Session's `__Host-SESSION`.
- Sessions appear in Redis as `spring:session:sessions:<id>` keys and survive an app restart.
- Tokens stored as session attributes (the next step) inherit the session's lifecycle for free: TTL, logout cleanup, horizontal scaling.
- The Actuator health endpoint includes a Redis health indicator, so `/actuator/health` is `UP` only when Redis is reachable — the docker-compose healthcheck relies on this.

> **Spring Boot 4 gotcha.** Boot 4 ships session auto-configuration in its own module (`spring-boot-session-data-redis`), bundled by the starter. Depending on the plain `org.springframework.session:spring-session-data-redis` jar alone compiles and boots fine but **silently leaves the in-memory container session in place** (the tell: responses set `JSESSIONID` instead of `__Host-SESSION`). Use the starter.

## Running

```bash
# Start the shared Redis instance (no env vars needed)
docker compose up -d bff-server-redis

# Build the embedded frontend and run the full-stack service
cd web-client-bff
WEB_CLIENT_SECRET=secret mvn spring-boot:run
```

The embedded frontend is served at `http://localhost:8083/`. Redis must be reachable; auth-server and its DB must also be running for OAuth login and refresh-token exchange — see `auth-server/README.md`.

Sanity check — the first request gets a session, and it lands in Redis:

```bash
curl -i http://localhost:8083/actuator/health     # expect 200, {"status":"UP"}, Set-Cookie: __Host-SESSION=...
docker exec bff-server-redis redis-cli --scan --pattern "spring:session:*"
```

The `web-client-bff` application is not yet defined in Docker Compose or CI/CD. The Redis command starts the existing shared BFF Redis service.

The `auth-server` client seed now permits `http://localhost:8083/logout` for the embedded app while preserving the standalone `http://localhost:3000/logout` URI. MySQL only runs initialization scripts on its first initialization; for an existing local database, add the embedded URI to both browser-client rows:

```sql
UPDATE oauth2_registered_client
SET post_logout_redirect_uris = CONCAT(post_logout_redirect_uris, ',http://localhost:8083/logout')
WHERE client_id IN ('WEB_CLIENT', 'WEB_CLIENT_PKCE')
  AND post_logout_redirect_uris NOT LIKE '%http://localhost:8083/logout%';
```

## Integration Tests

Integration tests in `src/test/java/com/roots/web_client_bff/integration/` hit a **live running** web-client-bff (plus its Redis) rather than spinning up a Spring context. There is deliberately no host-run `contextLoads` test. The tests exercise session issuance and the cookie→Redis round trip.

### Prerequisites

Start Redis and web-client-bff as shown under [Running](#running), plus a live **auth-server** (with its DB) at `localhost:9000` — the tests perform real guest logins there.

### Test properties

| Property | Default | Description |
|---|---|---|
| `bff-server-location` | `http://localhost:8083` | Existing test property for the base URL of the running web-client-bff |
| `auth-server-location` | `http://localhost:9000` | Base URL of the running auth-server |
| `web-client-location` | `http://localhost:3000` | OAuth2 redirect URI origin for the guest flow |
| `web-client-id` / `web-client-secret` | `WEB_CLIENT` / `secret` | Client credentials for the guest code exchange (must match the DB seed) |
| `redis-host` / `redis-port` | `localhost` / `6379` | Where the test seeds token keys directly |

Declared in `src/test/resources/application.yml`; override with `-D<property>=<value>`.

### Running

```bash
mvn surefire:test '-Dtest=%regex[.*integration.*]'
```

### AuthStatusIntegrationTest

Covers all four `/api/auth/status` paths with **genuine tokens**. All HTTP contact goes through per-server client classes in the test `client/` package (mirroring auth-server's test layout; each owns and configures its own `HttpClient`s, is `AutoCloseable`, and is built fresh per test): `BffClient.getLoginStatus(sessionCookie)` calls the status endpoint, and `AuthServerClient.fetchGuestTokens()` (a slimmed port of auth-server's integration-test client) drives a real guest login — authorize → `POST /login/guest` → redirect chain → code exchange — because guest needs no account fixture yet yields all three tokens. The test derives its own session id by base64-decoding its `__Host-SESSION` cookie, then seeds and asserts token keys through the autowired `TestTokenStoreService` — a test-side counterpart of the main store (same `<sessionId>:<tokenName>` keys, connected via the published Redis port, extended with TTL reads and bulk teardown), defined as a bean in `TestConfig` so the cached test context shares one Lettuce connection across the whole suite:

| Seeded | Expectation |
|---|---|
| nothing | `{"isLoggedIn": false}` with the claim fields absent |
| `id_token` | logged in; `email` = `guest`, `roles` contains `GUEST`, no `userGUID` field |
| `refresh_token` only | logged in via a live refresh exchange; fresh `id_token`/`access_token` keys appear with real TTLs; the stored refresh token is a **rotated** one (differs from the seeded value) |
| a garbage refresh token | `{"isLoggedIn": false}` and the refresh-token key is deleted |

Member-account id_token claims (a non-null `userGUID`) are asserted in **auth-server's** own integration suite (`LoginIntegrationTest`), where the account fixtures already live — the bff suite stays account-management-free.

### AuthorizeIntegrationTest

Covers `GET /api/auth/authorize` at two depths:

1. **Contract** — asserts the raw 302: every query parameter of the authorize URL (`response_type`, `client_id`, `redirect_uri`, `scope`, `state`), and that the minted `state` sits in Redis at `<sessionId>:oauth_state` with a positive TTL (the session id comes from the response's own `__Host-SESSION` cookie).
2. **Acceptance** — plays the browser: follows the emitted Location through a real guest login (`AuthServerClient.completeGuestLogin`) and asserts the web-client callback receives a non-blank `code` plus **exactly the bff-held state** — proof that auth-server accepts the bff-built URL end-to-end.

## CI/CD and Docker Compose

No dedicated CI/CD workflows or Docker Compose service have been added for web-client-bff yet. The existing `bff-server` workflows and Compose service remain unchanged.
