# bff-server

The **backend-for-frontend** for the roots-app platform. Its end goal is to manage OAuth2 tokens on behalf of `web-client`, so tokens no longer need to be stored in the browser (today web-client keeps them in `sessionStorage`). The browser holds only a `SESSION` cookie; the tokens live server-side in **Redis, keyed by the session id**.
The **backend-for-frontend** for the roots-app platform. Its end goal is to manage OAuth2 tokens on behalf of `web-client`, so tokens no longer need to be stored in the browser (today web-client keeps them in `sessionStorage`). The browser holds only a `SESSION` cookie; the tokens live server-side in **Redis, keyed by the session id**.
The **backend-for-frontend** for the roots-app platform. Its end goal is to manage OAuth2 tokens on behalf of `web-client`, so tokens no longer need to be stored in the browser (today web-client keeps them in `sessionStorage`). The browser will hold only a `SESSION` cookie; the tokens themselves will live server-side as attributes of a **Redis-backed HTTP session**.

> **Current state.** The login-status endpoint (`GET /api/auth/status`, below) is implemented and fully tested, but the authorization-code callback that writes the *initial* tokens to Redis still lives in web-client — so in real traffic the endpoint answers `isLoggedIn=false` until that move lands. Everything else remains foundation: the security posture, Redis-backed sessions, the docker-compose topology, and the CI/CD pipelines.
> **Current state.** The login-status endpoint (`GET /api/auth/status`, below) is implemented and fully tested, but the authorization-code callback that writes the *initial* tokens to Redis still lives in web-client — so in real traffic the endpoint answers `isLoggedIn=false` until that move lands. Everything else remains foundation: the security posture, Redis-backed sessions, the docker-compose topology, and the CI/CD pipelines.
> **Current state: scaffolding only.** There are no controllers, no token-relay endpoints, and no OAuth2 client wiring toward auth-server yet. What exists is the foundation those features will be built on: the security posture, Redis-backed sessions, the docker-compose topology, a smoke integration test, and the CI/CD pipelines.

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `SERVER_PORT` | No | `8083` | HTTP port the server listens on |
| `REDIS_HOST` | No | `localhost` | Redis host backing Spring Session (docker-compose sets `bff-server-redis`) |
| `REDIS_HOST` | No | `localhost` | Redis host backing Spring Session + the token store (docker-compose sets `bff-server-redis`) |
| `REDIS_HOST` | No | `localhost` | Redis host backing Spring Session + the token store (docker-compose sets `bff-server-redis`) |
| `REDIS_PORT` | No | `6379` | Redis port |
| `WEB_CLIENT_ORIGIN` | No | `http://localhost:3000` | The **only** origin allowed by CORS (property `web.client.origin`) |
| `AUTH_SERVER_INTERNAL_LOCATION` | No | `http://localhost:9000` | Auth-server base URL reachable from **inside** the deployment network; used by the server-to-server RestClient (refresh-token exchange). Property `auth-server.internal-location`; docker-compose sets `http://auth-server:9000` |
| `AUTH_SERVER_EXTERNAL_LOCATION` | No | `http://localhost:9000` | Auth-server base URL reachable from **outside** — i.e. by the user's browser; used in redirects the browser follows (the authorize kick-off). Property `auth-server.external-location`. Separate from the internal one because in docker `auth-server:9000` doesn't resolve outside the compose network — compose leaves this at the default |
| `WEB_CLIENT_ID` | No | `WEB_CLIENT` | OAuth2 client id the bff authenticates as (property `web.client.id`) |
| `WEB_CLIENT_SECRET` | **Yes** | — (no default) | Client secret for the above — must match the `WEB_CLIENT` `client_secret` seeded in auth-server's `oauth2_registered_client` table (`{noop}secret` in dev). Startup fails fast without it |
| `REFRESH_TOKEN_TTL_SECONDS` | No | `3600` | Redis TTL applied to stored refresh tokens (property `token-store.refresh-token-ttl-seconds`); mirrors auth-server's `refresh-token-time-to-live` |

## Login status — `GET /api/auth/status`

The endpoint web-client calls to ask "does this browser have a valid login?". Session-cookie driven, `permitAll`, and **always 200** — "not logged in" is a normal answer, not an error.

**Token store.** Each session's tokens live under three plain Redis string keys, each with its own TTL:

```
<sessionId>:access_token    TTL = the JWT's own exp
<sessionId>:refresh_token   TTL = REFRESH_TOKEN_TTL_SECONDS (opaque token, no readable exp)
<sessionId>:id_token        TTL = the JWT's own exp
```

`<sessionId>` is the Spring Session id — the base64-decoded value of the `SESSION` cookie. Because every key expires exactly when its token does, **an absent key is the expiry check**: reads never inspect `exp`.

**Flow** (`AuthController` → `AuthStatusService`):

1. `<sessionId>:id_token` present → logged in. Its payload is decoded (no signature verification — the bff itself stored it, and it originally came from auth-server over a server-to-server call) and returned as `{"isLoggedIn": true, "email": …, "userGUID": …, "roles": […]}`. A guest login has no `user_credential` row, so its response carries no `userGUID` field.
2. No id_token, but `<sessionId>:refresh_token` present → `AuthServerTokenClient` performs the `refresh_token` grant against `POST {auth-server.internal-location}/oauth2/token`, authenticating as **WEB_CLIENT** (refresh tokens are bound to the client they were issued to, so the bff must use the same registered client web-client's authorization codes are issued for). On success all three fresh tokens are stored and the id_token claims returned. auth-server seeds WEB_CLIENT with `reuse-refresh-tokens=false`, so every exchange **rotates** the refresh token — the stored one is always new.
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
  &redirect_uri={web.client.origin}/callback
  &scope=openid%20WEB_CLIENT_READ
  &state=<uuid>
```

- **state** is minted by the bff and stored at `<sessionId>:oauth_state` (TTL 5 minutes) — the bff, not the browser, owns CSRF validation when the flow returns. The future bff callback endpoint validates against this key; when web-client is repointed at this endpoint (a later step) its own sessionStorage state logic goes away. Until that repoint, nothing calls this endpoint in production.
- **redirect_uri** stays web-client's `/callback` for now (the only URI registered for WEB_CLIENT in the DB seed); moving the code exchange behind a bff callback is a later step.
- **No logged-in short-circuit**: if the auth-server session is already authenticated the flow completes silently without a login form; web-client should consult `/api/auth/status` first anyway.
- The Location uses `auth-server.external-location`, not `auth-server.internal-location` — the browser follows this redirect from outside the docker network, where the internal hostname doesn't resolve.

## Security Configuration

All security beans live in `config/SecurityConfig.java`:

- **`anyRequest().permitAll()`** — no endpoint-level authorization yet; access control will be introduced with the token-relay endpoints.
- **`SessionCreationPolicy.ALWAYS`** — every request eagerly gets an HTTP session, because the session is where tokens will be held on behalf of web-client. Even an anonymous `GET /actuator/health` receives a `SESSION` cookie.
- **CSRF disabled** — matches the project convention (auth-server disables it too). This must be revisited when state-changing BFF endpoints land: in this architecture the `SESSION` cookie is the browser's only credential, which is exactly the setup CSRF protection exists for.
- **CORS restricted to web-client** — only `web.client.origin` is an allowed origin, with `allowCredentials=true` so the browser may send the `SESSION` cookie on cross-origin requests. A preflight from any other origin is rejected with 403.

## Sessions in Redis (Spring Session)

HTTP sessions are stored in Redis via **Spring Session** (`spring-boot-starter-session-data-redis`), not in Tomcat memory:

- The servlet `JSESSIONID` cookie is replaced by Spring Session's `SESSION` cookie.
- Sessions appear in Redis as `spring:session:sessions:<id>` keys and survive an app restart.
- Tokens stored as session attributes (the next step) inherit the session's lifecycle for free: TTL, logout cleanup, horizontal scaling.
- The Actuator health endpoint includes a Redis health indicator, so `/actuator/health` is `UP` only when Redis is reachable — the docker-compose healthcheck relies on this.

> **Spring Boot 4 gotcha.** Boot 4 ships session auto-configuration in its own module (`spring-boot-session-data-redis`), bundled by the starter. Depending on the plain `org.springframework.session:spring-session-data-redis` jar alone compiles and boots fine but **silently leaves the in-memory container session in place** (the tell: responses set `JSESSIONID` instead of `SESSION`). Use the starter.

## Running

```bash
# 1. Start the Redis instance (no env vars needed)
docker compose up -d bff-server-redis

# 2. Run the service (from bff-server/); the secret must match auth-server's WEB_CLIENT seed
WEB_CLIENT_SECRET=secret mvn spring-boot:run
# 2. Run the service (from bff-server/)
mvn spring-boot:run
# 2. Run the service (from bff-server/); the secret must match auth-server's WEB_CLIENT seed
WEB_CLIENT_SECRET=secret mvn spring-boot:run
```

For the refresh-exchange path (and the integration tests), auth-server and its DB must also be running — see `auth-server/README.md`.

For the refresh-exchange path (and the integration tests), auth-server and its DB must also be running — see `auth-server/README.md`.

Sanity check — the first request gets a session, and it lands in Redis:

```bash
curl -i http://localhost:8083/actuator/health     # expect 200, {"status":"UP"}, Set-Cookie: SESSION=...
docker exec bff-server-redis redis-cli --scan --pattern "spring:session:*"
```

The full stack can also be run from images via `docker compose up -d --wait bff-server`, which chains in `bff-server-redis` and `auth-server` (and its DB) through `depends_on`. The auth-server dependency exists so the compose topology is ready for the upcoming token work — no bff-server code talks to auth-server yet.

## Integration Tests

Integration tests in `src/test/java/com/roots/bff_server/integration/` hit a **live running** bff-server (plus its Redis) rather than spinning up a Spring context. There is deliberately no host-run `contextLoads` test — the healthy container in CI is the de facto context-load proof (same reasoning as account-management).

### Prerequisites

Start Redis and bff-server as shown under [Running](#running). `AuthStatusIntegrationTest` additionally needs a live **auth-server** (with its DB) at `localhost:9000` — it performs a real guest login there to mint tokens.
Start Redis and bff-server as shown under [Running](#running).
Start Redis and bff-server as shown under [Running](#running). `AuthStatusIntegrationTest` additionally needs a live **auth-server** (with its DB) at `localhost:9000` — it performs a real guest login there to mint tokens.
Start Redis and bff-server as shown under [Running](#running), plus a live **auth-server** (with its DB) at `localhost:9000` — the tests perform real guest logins there.

### Test properties

| Property | Default | Description |
|---|---|---|
| `bff-server-location` | `http://localhost:8083` | Base URL of the running bff-server |
| `auth-server-location` | `http://localhost:9000` | Base URL of the running auth-server |
| `web-client-location` | `http://localhost:3000` | OAuth2 redirect URI origin for the guest flow |
| `web-client-id` / `web-client-secret` | `WEB_CLIENT` / `secret` | Client credentials for the guest code exchange (must match the DB seed) |
| `redis-host` / `redis-port` | `localhost` / `6379` | Where the test seeds token keys directly |

Declared in `src/test/resources/application.yml`; override with `-D<property>=<value>`.

### Running

```bash
mvn surefire:test '-Dtest=%regex[.*integration.*]'
```

### SessionSmokeIntegrationTest

Exercises the session foundation:
Exercises the session foundation:
Exercises exactly what this scaffolding stage sets up:

1. `GET /actuator/health` → asserts 200 and `UP` (which transitively proves the Redis connection, via the health indicator).
2. Asserts a `SESSION` cookie is issued on that first response — proof of `SessionCreationPolicy.ALWAYS` **and** that Spring Session (not the container) owns the session.
3. Replays the cookie on a second request and asserts **no new** `SESSION` cookie is set — the first session was found again, i.e. it round-tripped through the Redis store.

The test builds a fresh cookie-less `java.net.http.HttpClient` per test and closes it afterwards (the per-test lifecycle convention from auth-server's integration suite); cookies are asserted and replayed explicitly. `TestConfig` is an empty `@Configuration` that only anchors the test context so `@TestPropertySource`/`@Value` resolve.
`TestConfig` is an empty `@Configuration` that only anchors the test context so `@TestPropertySource`/`@Value` resolve. (The original `SessionSmokeIntegrationTest` was removed once the endpoint tests subsumed it: they exercise session issuance, the cookie→Redis round trip, and Redis connectivity on every run, and the compose healthcheck covers `/actuator/health`.)

### AuthStatusIntegrationTest

Covers all four `/api/auth/status` paths with **genuine tokens**. All HTTP contact goes through per-server client classes in the test `client/` package (mirroring auth-server's test layout; each owns and configures its own `HttpClient`s, is `AutoCloseable`, and is built fresh per test): `BffClient.getLoginStatus(sessionCookie)` calls the status endpoint, and `AuthServerClient.fetchGuestTokens()` (a slimmed port of auth-server's integration-test client) drives a real guest login — authorize → `POST /login/guest` → redirect chain → code exchange — because guest needs no account fixture yet yields all three tokens. The test derives its own session id by base64-decoding its `SESSION` cookie, then seeds and asserts token keys through the autowired `TestTokenStoreService` — a test-side counterpart of the main store (same `<sessionId>:<tokenName>` keys, connected via the published Redis port, extended with TTL reads and bulk teardown), defined as a bean in `TestConfig` so the cached test context shares one Lettuce connection across the whole suite:

| Seeded | Expectation |
|---|---|
| nothing | `{"isLoggedIn": false}` with the claim fields absent |
| `id_token` | logged in; `email` = `guest`, `roles` contains `GUEST`, no `userGUID` field |
| `refresh_token` only | logged in via a live refresh exchange; fresh `id_token`/`access_token` keys appear with real TTLs; the stored refresh token is a **rotated** one (differs from the seeded value) |
| a garbage refresh token | `{"isLoggedIn": false}` and the refresh-token key is deleted |

Member-account id_token claims (a non-null `userGUID`) are asserted in **auth-server's** own integration suite (`LoginIntegrationTest`), where the account fixtures already live — the bff suite stays account-management-free.

### AuthorizeIntegrationTest

Covers `GET /api/auth/authorize` at two depths:

1. **Contract** — asserts the raw 302: every query parameter of the authorize URL (`response_type`, `client_id`, `redirect_uri`, `scope`, `state`), and that the minted `state` sits in Redis at `<sessionId>:oauth_state` with a positive TTL (the session id comes from the response's own `SESSION` cookie).
2. **Acceptance** — plays the browser: follows the emitted Location through a real guest login (`AuthServerClient.completeGuestLogin`) and asserts the web-client callback receives a non-blank `code` plus **exactly the bff-held state** — proof that auth-server accepts the bff-built URL end-to-end.

## CI

The workflow at `.github/workflows/bff-server-ci.yml` runs on pull requests that touch `bff-server/src/**` or `bff-server/pom.xml` (events: `opened`, `synchronize`). Same shape as account-management-ci, minus a unit-test gate (there are no unit tests yet):

1. `docker login` — auth-server is an **unchanged dependency** here (the paths filter means the PR only touched bff-server), so its image is pulled as `:latest` rather than rebuilt.
2. Builds the JAR + test classes with `mvn package -DskipTests`.
3. Builds a local image via Jib: `mvn jib:dockerBuild -Djib.to.image=${DOCKERHUB_USERNAME}/bff-server:ci` (no registry push).
4. `docker compose up -d --wait bff-server` with `BFF_SERVER_TAG=ci` and `SPRING_PROFILES_ACTIVE=test` — `depends_on` chains in `bff-server-redis` and `auth-server` (which chains in the self-seeding DB). `--wait` blocks until everything is healthy; bff-server's healthcheck polls `/actuator/health`, whose Redis indicator proves the session store is reachable.
5. Runs the integration tests on the host against `localhost:8083`: `mvn surefire:test '-Dtest=%regex[.*integration.*]'`.
4. `docker compose up -d --wait bff-server` with `BFF_SERVER_TAG=ci`, `SPRING_PROFILES_ACTIVE=test`, and `WEB_CLIENT_SECRET=secret` — the secret is hardcoded in the workflow (not a GitHub secret) because it matches the `{noop}secret` WEB_CLIENT seed already public in the checked-in SQL. `depends_on` chains in `bff-server-redis` and `auth-server` (which chains in the self-seeding DB). `--wait` blocks until everything is healthy; bff-server's healthcheck polls `/actuator/health`, whose Redis indicator proves the session store is reachable.
5. Runs the integration tests on the host against `localhost:8083`: `mvn surefire:test '-Dtest=%regex[.*integration.*]'` — the session smoke test plus the four `/api/auth/status` paths (which drive a real guest login against the auth-server container).
4. `docker compose up -d --wait bff-server` with `BFF_SERVER_TAG=ci`, `SPRING_PROFILES_ACTIVE=test`, and `WEB_CLIENT_SECRET=secret` — the secret is hardcoded in the workflow (not a GitHub secret) because it matches the `{noop}secret` WEB_CLIENT seed already public in the checked-in SQL. `depends_on` chains in `bff-server-redis` and `auth-server` (which chains in the self-seeding DB). `--wait` blocks until everything is healthy; bff-server's healthcheck polls `/actuator/health`, whose Redis indicator proves the session store is reachable.
5. Runs the integration tests on the host against `localhost:8083`: `mvn surefire:test '-Dtest=%regex[.*integration.*]'` — the session smoke test plus the four `/api/auth/status` paths (which drive a real guest login against the auth-server container).
6. On failure, dumps all container logs (`docker compose logs --no-color`).

> **Ordering dependency:** the `/api/auth/status` tests assert id_token claims (`email`/`userGUID`/`roles`) that only exist once auth-server's id_token enrichment has merged and its CD has pushed a new `:latest` image. auth-server changes must land in their own PR **first**; a bff-server PR opened before that will fail CI against the stale image.

### Required GitHub secrets

| Secret | Value in CI |
|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub username — Jib image prefix and the `docker-compose.yml` image template |
| `DOCKERHUB_TOKEN` | Docker Hub access token — login for pulling the `auth-server:latest` dependency image |
| `MYSQL_AUTH_SERVER_ROOT_USERNAME` | `root` — for the auth-server dependency's DB |
| `MYSQL_AUTH_SERVER_ROOT_PASSWORD` | any password — same |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | real Gmail address + App Password — auth-server builds its `JavaMailSender` in every profile and its Actuator mail health indicator opens an SMTP connection on each health poll, so invalid creds would fail the `--wait` step |

The MySQL and mail secrets exist purely for the **auth-server dependency container** — bff-server itself needs no secrets; its only external dependency is Redis, which runs without auth in dev/CI.

## CD

The workflow at `.github/workflows/bff-server-cd.yml` triggers on every push to `main` that touches `bff-server/src/**` or `bff-server/pom.xml` (i.e. after a PR merges). Commits containing `[skip ci]` are ignored so the workflow's own version-bump commit doesn't retrigger it. Identical pattern to account-management-cd:

1. Reads the current `<version>` from `pom.xml` (e.g. `0.0.1-SNAPSHOT`).
2. Strips `-SNAPSHOT` and increments the patch digit to produce the release version (e.g. `0.0.2`).
3. Sets `pom.xml` to the release version with `mvn versions:set`.
4. Builds and pushes the image via Jib (`mvn jib:build -DskipTests`, base `eclipse-temurin:21-jre`), tagging both the release version and `latest`.
5. Sets `pom.xml` to the next SNAPSHOT and commits it back to `main` as `github-actions[bot]` with `[skip ci]`.

### Required GitHub secrets

| Secret | Description |
|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub username |
| `DOCKERHUB_TOKEN` | Docker Hub access token |
| `GH_PAT` | Personal access token used for checkout/push of the version-bump commit |

> The first CD push auto-creates the `bff-server` Docker Hub repository with the account's default visibility — check that it matches the other service repos.

## docker-compose topology

Two services were added to the root `docker-compose.yml`:

| Service | Image | Port | Notes |
|---|---|---|---|
| `bff-server-redis` | `redis:8` | `6379` (published) | No AUTH (dev/CI-grade, like the MySQL container); healthcheck `redis-cli ping` |
| `bff-server` | `${DOCKERHUB_USERNAME}/bff-server:${BFF_SERVER_TAG:-latest}` | `8083` (published) | `depends_on` `bff-server-redis` + `auth-server` (both healthy); compose sets `REDIS_HOST=bff-server-redis`; healthcheck curls `/actuator/health` |

Because CI workflows name their target service in `docker compose up -d --wait <service>`, adding bff-server to the compose file does not affect the other services' pipelines (a bare `up -d` would boot everything — auth-server-ci was scoped to `up -d --wait account-management` for exactly this reason).
