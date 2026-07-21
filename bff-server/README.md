# bff-server

The **backend-for-frontend** for the roots-app platform. Its end goal is to manage OAuth2 tokens on behalf of `web-client`, so tokens no longer need to be stored in the browser (today web-client keeps them in `sessionStorage`). The browser will hold only a `SESSION` cookie; the tokens themselves will live server-side as attributes of a **Redis-backed HTTP session**.

> **Current state: scaffolding only.** There are no controllers, no token-relay endpoints, and no OAuth2 client wiring toward auth-server yet. What exists is the foundation those features will be built on: the security posture, Redis-backed sessions, the docker-compose topology, a smoke integration test, and the CI/CD pipelines.

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `SERVER_PORT` | No | `8083` | HTTP port the server listens on |
| `REDIS_HOST` | No | `localhost` | Redis host backing Spring Session (docker-compose sets `bff-server-redis`) |
| `REDIS_PORT` | No | `6379` | Redis port |
| `WEB_CLIENT_ORIGIN` | No | `http://localhost:3000` | The **only** origin allowed by CORS (property `web.client.origin`) |

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

# 2. Run the service (from bff-server/)
mvn spring-boot:run
```

Sanity check — the first request gets a session, and it lands in Redis:

```bash
curl -i http://localhost:8083/actuator/health     # expect 200, {"status":"UP"}, Set-Cookie: SESSION=...
docker exec bff-server-redis redis-cli --scan --pattern "spring:session:*"
```

The full stack can also be run from images via `docker compose up -d --wait bff-server`, which chains in `bff-server-redis` and `auth-server` (and its DB) through `depends_on`. The auth-server dependency exists so the compose topology is ready for the upcoming token work — no bff-server code talks to auth-server yet.

## Integration Tests

Integration tests in `src/test/java/com/roots/bff_server/integration/` hit a **live running** bff-server (plus its Redis) rather than spinning up a Spring context. There is deliberately no host-run `contextLoads` test — the healthy container in CI is the de facto context-load proof (same reasoning as account-management).

### Prerequisites

Start Redis and bff-server as shown under [Running](#running).

### Test properties

| Property | Default | Description |
|---|---|---|
| `bff-server-location` | `http://localhost:8083` | Base URL of the running bff-server |

Declared in `src/test/resources/application.yml`; override with `-D<property>=<value>`.

### Running

```bash
mvn surefire:test '-Dtest=%regex[.*integration.*]'
```

### SessionSmokeIntegrationTest

Exercises exactly what this scaffolding stage sets up:

1. `GET /actuator/health` → asserts 200 and `UP` (which transitively proves the Redis connection, via the health indicator).
2. Asserts a `SESSION` cookie is issued on that first response — proof of `SessionCreationPolicy.ALWAYS` **and** that Spring Session (not the container) owns the session.
3. Replays the cookie on a second request and asserts **no new** `SESSION` cookie is set — the first session was found again, i.e. it round-tripped through the Redis store.

The test builds a fresh cookie-less `java.net.http.HttpClient` per test and closes it afterwards (the per-test lifecycle convention from auth-server's integration suite); cookies are asserted and replayed explicitly. `TestConfig` is an empty `@Configuration` that only anchors the test context so `@TestPropertySource`/`@Value` resolve.

## CI

The workflow at `.github/workflows/bff-server-ci.yml` runs on pull requests that touch `bff-server/src/**` or `bff-server/pom.xml` (events: `opened`, `synchronize`). Same shape as account-management-ci, minus a unit-test gate (there are no unit tests yet):

1. `docker login` — auth-server is an **unchanged dependency** here (the paths filter means the PR only touched bff-server), so its image is pulled as `:latest` rather than rebuilt.
2. Builds the JAR + test classes with `mvn package -DskipTests`.
3. Builds a local image via Jib: `mvn jib:dockerBuild -Djib.to.image=${DOCKERHUB_USERNAME}/bff-server:ci` (no registry push).
4. `docker compose up -d --wait bff-server` with `BFF_SERVER_TAG=ci` and `SPRING_PROFILES_ACTIVE=test` — `depends_on` chains in `bff-server-redis` and `auth-server` (which chains in the self-seeding DB). `--wait` blocks until everything is healthy; bff-server's healthcheck polls `/actuator/health`, whose Redis indicator proves the session store is reachable.
5. Runs the integration tests on the host against `localhost:8083`: `mvn surefire:test '-Dtest=%regex[.*integration.*]'`.
6. On failure, dumps all container logs (`docker compose logs --no-color`).

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
