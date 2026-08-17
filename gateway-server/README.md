# Gateway Server

**Spring Cloud Gateway** (WebFlux) — the single entry point for all traffic. Routes requests to downstream services (auth-server, bff-server, simple-resource-server, account-management) while enriching them with OAuth2 access tokens stored in Redis by the bff-server.

## Architecture

Gateway-server runs on **port 8080** and acts as a reverse proxy for the entire application:

- **All traffic flows through the gateway**, including OAuth2 redirects and login forms
- Browser session cookie (`__Host-SESSION`) is preserved through the gateway
- Gateway reads access tokens from **shared bff-server Redis** (port 6379) using the session ID decoded from the cookie
- Tokens are attached to upstream requests targeting protected resource servers
- Gateway is **stateless** — it does not own login/session lifecycle, but it can perform refresh-token exchange with auth-server when only a refresh token is present

### Token Lookup Flow

1. Browser sends request with `__Host-SESSION` cookie
2. Gateway decodes the session ID from the cookie
3. Gateway queries bff-server's Redis: `GET <sessionId>:access_token`
4. If access token exists, gateway attaches it to the upstream request as a bearer token
5. If access token is missing but refresh token exists, gateway exchanges it at auth-server `/oauth2/token` and stores new tokens back in Redis
6. Upstream service validates the token via JWT verification (fetching JWK from auth-server)

### Routing

Typical routes:

| Path | Upstream |
|---|---|
| `/oauth2/**` | auth-server:9000 |
| `/api/auth/**` | bff-server:8083 |
| `/api/account/**` | account-management:8082 |
| `/simple-resource-server/**` | simple-resource-server:8081 |
| `/**` (catch-all) | auth-server:9000 (serves SPA / login form) |

## Configuration

**`src/main/resources/application.yml`:**

```yaml
spring:
  application:
    name: gateway-server
  data:
    redis:
      host: ${REDIS_HOST:localhost}        # Shared bff-server Redis (compose: bff-server-redis)
      port: ${REDIS_PORT:6379}             # Default 6379 (not auth-server's 6381)

server:
  port: ${SERVER_PORT:8080}                # Gateway listen port

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL:http://localhost:8070/eureka/}  # Eureka registry

info:
  app:
    name: ${spring.application.name}
    description: API gateway service for roots-app
    version: @project.version@             # Filtered by Maven at build time

management:
  endpoints:
    web:
      exposure:
        include: health,info,shutdown      # Expose shutdown for de-registration
  endpoint:
    shutdown:
      access: unrestricted                 # Allow graceful shutdown
```

### Eureka Service Discovery

Gateway-server is a Spring Cloud Netflix Eureka client. On startup, it automatically registers itself with the Eureka server (default: `http://localhost:8070/eureka/`), making itself discoverable by other services.

**De-registration:** To gracefully de-register from Eureka:
```bash
curl -X POST http://localhost:8080/actuator/shutdown
```

This triggers a clean shutdown with proper Eureka de-registration before the process exits.

### Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| `REDIS_HOST` | `localhost` | Redis hostname (compose: `bff-server-redis`) |
| `REDIS_PORT` | `6379` | Redis port (shared with bff-server) |
| `SERVER_PORT` | `8080` | Gateway listen port |
| `EUREKA_SERVER_URL` | `http://localhost:8070/eureka/` | Eureka registry URL; compose sets `http://eureka-server:8070/eureka/` |
| `WEB_CLIENT_ORIGIN` | `http://localhost:3000` | Allowed browser origin for CORS |
| `WEB_CLIENT_ID` | `WEB_CLIENT` | OAuth2 client ID used for refresh-token exchange |
| `WEB_CLIENT_SECRET` | `secret` | OAuth2 client secret used for refresh-token exchange |
| `AUTH_SERVER_INTERNAL_LOCATION` | `http://localhost:9000` | Auth-server URL used by gateway for token exchange |
| `REFRESH_TOKEN_TTL_SECONDS` | `3600` | TTL used when persisting refreshed `refresh_token` |
| `SPRING_PROFILES_ACTIVE` | _(none)_ | Profile activation (e.g., `test` in CI) |

## Local Development

```bash
cd gateway-server

# Start the gateway locally (requires Redis running on localhost:6379)
mvn spring-boot:run

# Build jar + test classes
mvn package

# Run unit tests
mvn test
```

## Docker Compose

Gateway-server is part of the `docker-compose.yml` stack:

```bash
# Start gateway + all dependencies (bff-server-redis, auth-server-redis, auth-server-db, auth-server, bff-server)
docker compose up -d --wait gateway-server

# Gateway health check
curl http://localhost:8080/actuator/health

# View logs
docker compose logs -f gateway-server
```

### Compose Dependencies

Gateway-server depends on:
1. `bff-server-redis` (healthy) — the shared token store
2. `bff-server` (healthy) — gateway chains this upstream service
   - Which transitively depends on `auth-server` (healthy)
     - Which transitively depends on `auth-server-db` (healthy)

## CI / CD

### CI Pipeline (`gateway-server-ci.yml`)

**Trigger:** Pull request `opened`/`synchronize` on paths `gateway-server/src/**` or `gateway-server/pom.xml`

**Steps:**
1. Checkout and setup JDK 21 + Maven cache
2. Run unit tests first (fast-fail gate): `mvn test -Dtest="GatewayServerApplicationTests,AccessTokenFilterTest,RefreshTokenFilterTest"`
3. Docker login (pull private `:latest` images of bff-server, auth-server)
4. Build gateway-server jar: `mvn package -DskipTests`
5. Build gateway-server image: `mvn jib:dockerBuild -Djib.to.image=...:ci`
6. `docker compose up -d --wait gateway-server` (chains in dependencies, blocks until all healthy)
7. Verify health: `curl http://localhost:8080/actuator/health | grep UP`
8. Run integration tests after the stack is healthy: `mvn surefire:test -Dtest="GuestRoleGatewayIntegrationTest"`
9. Dump logs on failure

**Required GitHub Secrets:**
- `DOCKERHUB_USERNAME` — for pulling/building images
- `DOCKERHUB_TOKEN` — for Docker Hub authentication
- `MYSQL_AUTH_SERVER_ROOT_USERNAME` / `MYSQL_AUTH_SERVER_ROOT_PASSWORD` — for DB container
- `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` — for auth-server mail bean healthcheck

### CD Pipeline (`gateway-server-cd.yml`)

**Trigger:** Push to `main` on paths `gateway-server/src/**` or `gateway-server/pom.xml`

**Steps:**
1. Checkout and setup JDK 21 + Maven cache
2. Docker login
3. Read current version from `pom.xml`, compute release version (bump patch digit)
4. `mvn versions:set -DnewVersion=<release-version>` 
5. `mvn jib:build -DskipTests` — push `<release-version>` and `latest` tags to Docker Hub
6. `mvn versions:set -DnewVersion=<next-snapshot>`
7. Commit + push version bump as `github-actions[bot]` with `[skip ci]` trailer

**Required GitHub Secrets:**
- `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` — for push
- `GH_PAT` — GitHub personal access token for pushing version-bump commit

## Integration Testing

Gateway-server includes an integration test (`GuestRoleGatewayIntegrationTest`) that exercises the guest-login flow through the gateway and validates guest endpoint access.
