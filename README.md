# roots-app

A Spring Cloud microservices application providing authentication, authorization, and role-based resource access.

## Services

| Service | Tech | Port | Role |
|---|---|---|---|
| `eureka-server` | Spring Cloud Netflix Eureka | — | Service discovery registry |
| `config-server` | Spring Cloud Config | — | Centralized configuration |
| `gateway-server` | Spring Cloud Gateway (WebFlux) | — | API gateway / routing |
| `auth-server` | Spring Boot + Nuxt/Vue | 9000 | Authentication + embedded SSR frontend |
| `bff-server` | Spring Boot | — | Backend-for-frontend |
| `simple-resource-server` | Spring Boot | 8081 | Example protected resource with role endpoints |
| `web-client` | Nuxt 4 / Vue 3 | 3000 | Standalone frontend |

**Startup order:** config-server → eureka-server → gateway-server → auth-server → bff-server → simple-resource-server → web-client.

## CI

GitHub Actions workflows are defined in `.github/workflows/`. Each workflow runs on pull requests that touch the relevant service directory.

| Workflow | Trigger path | What it does |
|---|---|---|
| `auth-server-ci.yml` | `auth-server/**` | Starts a MySQL service container, seeds the DB, builds the JAR, starts auth-server, and runs integration tests |
| `simple-resource-server-ci.yml` | `simple-resource-server/**` | Runs `mvn test` to verify the service context loads |

See each service's README for the secrets required by its workflow.
