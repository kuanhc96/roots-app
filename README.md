# roots-app

A Spring Cloud microservices application that combines a Spring Authorization Server, a token-managing BFF, a gateway, and multiple resource services behind a shared database and Redis-backed session layer.

## Service map

| Service | Tech | Port | Role |
|---|---|---|---|
| `eureka-server` | Spring Cloud Netflix Eureka | 8070 (internal registry) | Service discovery registry |
| `config-server` | Spring Cloud Config | configurable | Centralized configuration |
| `gateway-server` | Spring Cloud Gateway (WebFlux) | 8080 | Single entry point; attaches OAuth2 tokens from shared Redis and routes all traffic |
| `auth-server` | Spring Boot + Nuxt/Vue | 9000 | OAuth2 Authorization Server + embedded SPA frontend |
| `bff-server` | Spring Boot | 8083 | Backend-for-frontend; owns token storage in Redis and manages browser-facing auth state |
| `account-management-bff` | Spring Boot | 8084 | BFF for the account-management client; stores tokens in Redis under a separate `__Host-AMC_SESSION` session |
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
6. `bff-server`
7. `account-management-bff` (for the account-management client flow)
8. `gateway-server`

Then separately, as needed:

- `simple-resource-server`
- `account-management`
- `web-client`

## Runtime model

The architecture has moved beyond a simple auth-server + resource-server pattern:

- `auth-server` is the Authorization Server and also serves the embedded Nuxt frontend.
- `bff-server` owns the browser session for the main web-client flow and stores access/refresh/id tokens in Redis keyed by the session ID.
- `account-management-bff` mirrors that pattern for the account-management client, using a separate `__Host-AMC_SESSION` cookie and the `ACCOUNT_MANAGEMENT_CLIENT` / `ACCOUNT_MANAGEMENT_SUPER_CLIENT` confidential PKCE clients.
- `gateway-server` sits in front of downstream services, reads the session's token data from the shared Redis, and attaches bearer tokens before routing requests.
- `simple-resource-server` and `account-management` validate JWTs from `auth-server` and enforce scope/role checks with `@PreAuthorize`.
- `account-management` shares the same MySQL schema as `auth-server` (`user_credential` and `role`) and is used for integration fixtures and account lifecycle testing.
- `web-client` does not hold tokens client-side; it relies on `bff-server` for status, authorize, callback, and logout flows, while the account-management client uses the dedicated BFF.

## Local infrastructure

The root `docker-compose.yml` wires up the shared test stack on the `roots_backend` network.

- `auth-server-db` runs MySQL 8 on port `3308`
- `auth-server-redis` runs Redis on port `6381`
- `bff-server-redis` runs Redis on port `6379`
- `auth-server` exposes port `9000`
- `account-management` exposes port `8082`
- `bff-server` exposes port `8083`
- `account-management-bff` exposes port `8084`
- `gateway-server` exposes port `8080`

The database self-seeds from `auth-server/src/main/resources/initialize_db/` on first startup, and the Compose stack uses `docker compose up -d --wait ...` with health checks in CI and local verification.

## Key configuration and security notes

- `auth-server` uses MySQL on `localhost:3308` by default (`MYSQL_AUTH_SERVER_DB_URL`).
- `auth-server` requires `MYSQL_AUTH_SERVER_ROOT_USERNAME` and `MYSQL_AUTH_SERVER_ROOT_PASSWORD` at startup.
- Spring Session uses a secure `__Host-AUTH_SESSION` cookie for the auth-server session, while bff-server uses its own `__Host-SESSION` cookie.
- `NUXT_PUBLIC_GOOGLE_CLIENT_SECRET` is a frontend build-time variable, not a runner-time JVM variable, because the embedded Nuxt app is statically generated.
- The mail credentials (`SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`) are required in every profile because the `JavaMailSender` is built regardless of profile and its health check opens an SMTP connection.
- `gateway-server`, `bff-server`, and `account-management-bff` all depend on the shared Redis store used by the browser-backed token flow.
- `account-management-bff` authenticates as the `ACCOUNT_MANAGEMENT_CLIENT` / `ACCOUNT_MANAGEMENT_SUPER_CLIENT` confidential PKCE clients and expects `ACCOUNT_MANAGEMENT_CLIENT_SECRET` to match the seeded `{noop}secret` value in the auth-server DB.

## Repository layout

- `auth-server/` — OAuth2 Authorization Server and embedded Nuxt frontend
- `bff-server/` — session-backed token broker for the main web-client
- `account-management-bff/` — session-backed token broker for the account-management client
- `gateway-server/` — API gateway and token relay
- `simple-resource-server/` — sample protected resource app
- `account-management/` — shared-account CRUD service for testing
- `web-client/` — standalone Nuxt app
- `eureka-server/` and `config-server/` — service discovery and configuration

## CI and CD

GitHub Actions workflows live in `.github/workflows/` and cover the service-specific CI/CD flows for `auth-server`, `account-management`, `bff-server`, `gateway-server`, and `simple-resource-server`.

The current workflow pattern is:

- PRs run targeted CI: unit checks, local image builds, Docker Compose boot of dependencies, then service-specific integration tests.
- `main` pushes trigger the release version bump, Jib image push, and the next SNAPSHOT commit.
- `account-management-bff` also has its own CI workflow (`.github/workflows/account-management-bff-ci.yml`), which boots the service with Redis + auth-server and health-checks the `/actuator/health` endpoint on port `8084`.

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
cd bff-server && WEB_CLIENT_SECRET=secret mvn spring-boot:run
cd account-management-bff && ACCOUNT_MANAGEMENT_CLIENT_SECRET=secret mvn spring-boot:run
cd gateway-server && mvn spring-boot:run
cd simple-resource-server && mvn spring-boot:run
cd account-management && mvn spring-boot:run
cd web-client && npm install && npm run dev
```

## Further reading

See the service READMEs for the operational details and workflow-specific instructions:

- `auth-server/README.md`
- `bff-server/README.md`
- `account-management-bff/README.md`
- `gateway-server/README.md`
- `account-management/README.md`
- `simple-resource-server/README.md`
- `web-client/README.md`
