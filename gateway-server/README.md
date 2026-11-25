# Gateway Server

**Spring Cloud Gateway** (WebFlux) routes requests to downstream resource services, preserving caller-supplied bearer tokens. web-client-bff serves browser traffic directly and sends its outbound role API calls through the gateway with a bearer token. The gateway has no Redis connection.

## Architecture

Gateway-server runs on **port 8080** and acts as a reverse proxy for the entire application:

- web-client-bff's SPA, login lifecycle, and callback are not gateway routes and require no Eureka registration
- The BFF owns browser sessions, access tokens, and refresh-token exchange using Spring Security OAuth2 Client
- The gateway forwards the supplied `Authorization` header to protected resource servers
- Gateway is **stateless**: it does not resolve session cookies, store tokens, or perform OAuth2 token exchanges

### Bearer Token Flow

1. Browser calls the BFF with its session cookie.
2. The BFF resolves or refreshes its authorized client's access token.
3. The BFF calls the gateway with `Authorization: Bearer <access_token>`, without a browser session cookie.
4. The gateway routes the request and preserves the bearer header.
5. The resource server validates the JWT and enforces scope/role checks. A session cookie alone does not grant resource access through the gateway.

### Routing

Gateway routing behavior:

| Path | Upstream |
|---|---|
| `/roots-app/simple-resource-server/**` | `SIMPLE-RESOURCE-SERVER` via a custom route (prefix rewritten to `/api/`) |
| `/roots-app/account-management/**` | `ACCOUNT-MANAGEMENT` via a custom route (prefix rewritten to `/api/`) |
| `/auth-server/**` | `AUTH-SERVER` via discovery-locator route (service-id prefix) |
| `/account-management/**` | `ACCOUNT-MANAGEMENT` via discovery-locator route (service-id prefix) |

## Configuration

**`src/main/resources/application.yml`:**

```yaml
spring:
  application:
    name: gateway-server

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
        include: health,info,shutdown,gateway      # Expose gateway + shutdown endpoints
  endpoint:
    gateway:
      access: unrestricted
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
| `SERVER_PORT` | `8080` | Gateway listen port |
| `EUREKA_SERVER_URL` | `http://localhost:8070/eureka/` | Eureka registry URL; compose sets `http://eureka-server:8070/eureka/` |
| `WEB_CLIENT_ORIGIN` | `http://localhost:8083` | Allowed web-client browser origin for CORS |
| `ACCOUNT_MANAGEMENT_CLIENT_ORIGIN` | `http://localhost:8084` | Allowed account-management browser origin for CORS |
| `SPRING_PROFILES_ACTIVE` | _(none)_ | Profile activation (e.g., `test` in CI) |

## Local Development

```bash
cd gateway-server

# Start the gateway locally (no Redis connection required)
mvn spring-boot:run

# Build jar + test classes
mvn package

# Check startup and resource routes without external services
mvn test -Dtest="GatewayServerApplicationTests"

# Run live integration tests after starting dependencies
mvn surefire:test -Dtest="GuestRoleGatewayIntegrationTest"
```

## Docker Compose

Gateway-server is part of the `docker-compose.yml` stack:

```bash
# Start gateway + its registry, resource services and auth dependencies
docker compose up -d --wait gateway-server

# Gateway health check
curl http://localhost:8080/actuator/health

# View logs
docker compose logs -f gateway-server
```

### Compose Dependencies

Gateway-server depends on:
1. `eureka-server` (healthy) — service discovery for `lb://...` targets
2. `simple-resource-server` and `account-management` (healthy), which bring in auth-server and its DB/Redis dependencies

The gateway itself does not depend on `bff-server-redis`. Auth-server's separate Redis-backed sessions remain unchanged.

There is no startup dependency on web-client-bff. Instead, web-client-bff waits for the gateway to be healthy, avoiding a startup cycle.

## CI / CD

### CI Pipeline (`gateway-server-ci.yml`)

**Trigger:** Pull request `opened`/`synchronize` on paths `gateway-server/src/**` or `gateway-server/pom.xml`

**Steps:**
1. Checkout and setup JDK 21 + Maven cache
2. Docker login (pull private `:latest` dependency images, including auth-server)
3. Build gateway-server jar: `mvn package -DskipTests`, then run `GatewayServerApplicationTests` to confirm startup and routing without Redis
4. Build gateway-server image: `mvn jib:dockerBuild -Djib.to.image=...:ci`
5. Build web-client-bff's embedded SPA/JAR and local `:ci` image as the login fixture, then `docker compose up -d --wait web-client-bff` (starts the local gateway image and dependencies first; no initial web-client-bff CD release is required)
6. Verify health: `curl http://localhost:8080/actuator/health | grep UP`
7. Run integration tests after the stack is healthy: `mvn surefire:test -Dtest="GuestRoleGatewayIntegrationTest"`
8. Dump logs on failure

**Required GitHub Secrets:**
- `DOCKERHUB_USERNAME` — for pulling/building images
- `DOCKERHUB_TOKEN` — for Docker Hub authentication
- `MYSQL_AUTH_SERVER_ROOT_USERNAME` / `MYSQL_AUTH_SERVER_ROOT_PASSWORD` — for DB container
- `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` — for auth-server mail bean healthcheck
- `GOOGLE_CLIENT_SECRET` — for auth-server's server-side Google token exchange configuration

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

Gateway-server includes an integration test (`GuestRoleGatewayIntegrationTest`) that exercises guest login directly through web-client-bff, then validates guest endpoint access through the gateway. The old BFF route and its Eureka registration requirement have been removed.
