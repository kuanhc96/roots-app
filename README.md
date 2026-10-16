# roots-app

A Spring Cloud microservices application that combines a Spring Authorization Server, a token-managing BFF, a gateway, and multiple resource services behind a shared database and Redis-backed session layer.

## Service map

| Service | Tech | Port | Role |
|---|---|---|---|
| `eureka-server` | Spring Cloud Netflix Eureka | 8070 (internal registry) | Service discovery registry |
| `config-server` | Spring Cloud Config | configurable | Centralized configuration |
| `gateway-server` | Spring Cloud Gateway (WebFlux) | 8080 | Routes internal resource APIs and supports token enrichment from shared Redis |
| `auth-server` | Spring Boot + Nuxt/Vue | 9000 | OAuth2 Authorization Server + embedded SPA frontend |
| `web-client-bff` | Spring Boot + Nuxt/Vue | 8083 | Embedded web-client SPA and BFF; owns browser sessions and OAuth2 tokens in Redis |
| `account-management-client-bff` | Spring Boot + Nuxt/Vue | 8084 | Embedded account-management SPA and BFF; stores tokens in Redis under a separate `__Host-AMC_SESSION` session |
| `account-management-bff` | Spring Boot | internal 8084 | Legacy account-management BFF retained as a gateway dependency; its host port is not published |
| `simple-resource-server` | Spring Boot | 8081 | Example protected resource server with role-based endpoints |
| `account-management` | Spring Boot | 8082 | Shared-db account CRUD service used for integration fixtures and testing |
| `web-client` | Nuxt 4 / Vue 3 | 3000 | Standalone browser app |

## Startup order

The current stack is intended to start in this order:

1. `eureka-server`
2. `config-server`
3. `auth-server-db`
4. `auth-server`
5. `bff-server-redis`
6. `account-management-bff`, `simple-resource-server`, and `account-management`
7. `gateway-server`
8. `web-client-bff` (waits for healthy gateway-server, auth-server, and shared Redis)
9. `account-management-client-bff` (waits for healthy gateway-server, auth-server, and shared Redis)

Then separately, as needed:

- `web-client`

## Runtime model

The architecture has moved beyond a simple auth-server + resource-server pattern:

- `auth-server` is the Authorization Server and also serves the embedded Nuxt frontend.
- `web-client-bff` serves the embedded SPA directly, owns its browser session, and stores access/refresh/id tokens in Redis keyed by the session ID. It does not register with Eureka or sit behind gateway-server; its outbound role calls use the gateway.
- `account-management-client-bff` embeds the account-management SPA and mirrors the web-client BFF flow, using a separate `__Host-AMC_SESSION` cookie and the `ACCOUNT_MANAGEMENT_CLIENT` / `ACCOUNT_MANAGEMENT_SUPER_CLIENT` confidential PKCE clients.
- `gateway-server` sits in front of downstream services, reads the session's token data from the shared Redis, and attaches bearer tokens before routing requests.
- `simple-resource-server` and `account-management` validate JWTs from `auth-server` and enforce scope/role checks with `@PreAuthorize`.
- `account-management` shares the same MySQL schema as `auth-server` (`user_credential` and `role`) and is used for integration fixtures and account lifecycle testing.
- The embedded web-client does not hold tokens client-side; it uses same-origin BFF endpoints for status, authorize, callback, logout, and role calls. The standalone `web-client/` and old `bff-server/` source directories remain, but the old BFF is no longer part of Compose or CI/CD.

## Local infrastructure

The root `docker-compose.yml` wires up the shared test stack on the `roots_backend` network.

- `auth-server-db` runs MySQL 8 on port `3308`
- `auth-server-redis` runs Redis on port `6381`
- `bff-server-redis` runs Redis on port `6379`
- `auth-server` exposes port `9000`
- `account-management` exposes port `8082`
- `web-client-bff` exposes port `8083`
- `account-management-client-bff` exposes port `8084`
- `gateway-server` exposes port `8080`

The database self-seeds from `auth-server/src/main/resources/initialize_db/` on first startup, and the Compose stack uses `docker compose up -d --wait ...` with health checks in CI and local verification.

## Key configuration and security notes

- `auth-server` uses MySQL on `localhost:3308` by default (`MYSQL_AUTH_SERVER_DB_URL`).
- `auth-server` requires `MYSQL_AUTH_SERVER_ROOT_USERNAME` and `MYSQL_AUTH_SERVER_ROOT_PASSWORD` at startup.
- Spring Session uses a secure `__Host-AUTH_SESSION` cookie for the auth-server session, while web-client-bff uses its own `__Host-SESSION` cookie.
- `NUXT_PUBLIC_GOOGLE_CLIENT_SECRET` is a frontend build-time variable, not a runner-time JVM variable, because the embedded Nuxt app is statically generated.
- The mail credentials (`SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`) are required in every profile because the `JavaMailSender` is built regardless of profile and its health check opens an SMTP connection.
- `gateway-server`, `web-client-bff`, and `account-management-client-bff` all depend on the shared `bff-server-redis` store used by the browser-backed token flow.
- `account-management-bff` authenticates as the `ACCOUNT_MANAGEMENT_CLIENT` / `ACCOUNT_MANAGEMENT_SUPER_CLIENT` confidential PKCE clients and expects `ACCOUNT_MANAGEMENT_CLIENT_SECRET` to match the seeded `{noop}secret` value in the auth-server DB.

## Repository layout

- `auth-server/` — OAuth2 Authorization Server and embedded Nuxt frontend
- `web-client-bff/` — embedded Nuxt SPA and session-backed token broker
- `bff-server/` — legacy BFF source (no longer deployed by Compose or CI/CD)
- `account-management-client-bff/` — embedded account-management SPA and session-backed token broker
- `account-management-bff/` — legacy BFF retained as a gateway dependency
- `gateway-server/` — API gateway and token relay
- `simple-resource-server/` — sample protected resource app
- `account-management/` — shared-account CRUD service for testing
- `web-client/` — standalone Nuxt app
- `eureka-server/` and `config-server/` — service discovery and configuration

## CI and CD

GitHub Actions workflows live in `.github/workflows/` and cover the service-specific CI/CD flows for `auth-server`, `account-management`, `web-client-bff`, `account-management-client-bff`, `gateway-server`, and `simple-resource-server`.

The current workflow pattern is:

- PRs run targeted CI: unit checks, local image builds, Docker Compose boot of dependencies, then service-specific integration tests.
- `main` pushes trigger the release version bump, Jib image push, and the next SNAPSHOT commit.
- `account-management-client-bff` CI runs its unit tests, starts the service after gateway-server and auth-server, then health-checks `/actuator/health` on port `8084`.

For service-specific setup, see the README in each service directory.

## Local commands

```bash
# Start shared infra
# mysql and redis are defined in the root docker-compose.yml
docker compose up -d auth-server-db
docker compose up -d auth-server-redis
docker compose up -d bff-server-redis

# Run a service from its own directory
cd auth-server && mvn spring-boot:run
cd web-client-bff && WEB_CLIENT_SECRET=secret mvn spring-boot:run
cd account-management-bff && ACCOUNT_MANAGEMENT_CLIENT_SECRET=secret mvn spring-boot:run
cd gateway-server && mvn spring-boot:run
cd simple-resource-server && mvn spring-boot:run
cd account-management && mvn spring-boot:run
cd web-client && npm install && npm run dev
```

## Further reading

See the service READMEs for the operational details and workflow-specific instructions:

- `auth-server/README.md`
- `web-client-bff/README.md`
- `account-management-client-bff/README.md`
- `account-management-bff/README.md`
- `gateway-server/README.md`
- `account-management/README.md`
- `simple-resource-server/README.md`
- `web-client/README.md`
