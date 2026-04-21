# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture Overview

This is a Spring Cloud microservices application called **roots-app**. The services are:

| Service | Tech | Port | Role |
|---|---|---|---|
| `eureka-server` | Spring Cloud Netflix Eureka | — | Service discovery registry |
| `config-server` | Spring Cloud Config | — | Centralized configuration |
| `gateway-server` | Spring Cloud Gateway (WebFlux) | — | API gateway / routing |
| `auth-server` | Spring Boot (Maven) + Nuxt/Vue | 9000 | Authentication + embedded SSR frontend |
| `bff-server` | Spring Boot (Maven) | — | Backend-for-frontend |
| `simple-resource-server` | Spring Boot (Maven) | — | Example protected resource |
| `web-client` | Nuxt 4 / Vue 3 | 3000 | Standalone frontend |

**Startup order:** config-server → eureka-server → gateway-server → auth-server → bff-server → simple-resource-server → web-client.

### auth-server is special

It bundles a Nuxt frontend via Maven. The Maven build:
1. Installs Node.js and runs `npm install` + `npm run generate` in `frontend/`
2. Copies `frontend/.output/public` into Spring Boot's `src/main/resources/static`
3. `SpaController` forwards `/` to `index.html` for client-side routing

The `auth-server-db` MySQL instance runs on port **3307** (not the default 3306) and is defined in `docker-compose.yml`. DB schema is in `auth-server/src/main/resources/initialize_db/`.

### web-client vs auth-server/frontend

- `web-client/` — standalone Nuxt 4 app, developed and deployed independently
- `auth-server/frontend/` — Nuxt app embedded inside auth-server's Spring Boot JAR via Maven build

## Commands

### Spring Boot services (all use Maven)

```bash
# From any service directory, e.g.:
cd bff-server

mvn spring-boot:run        # run the service
mvn package                # compile + test + jar
mvn test                   # run tests
mvn test -Dtest="SomeTest" # single test class
```

### auth-server (Maven + embedded Nuxt frontend)

```bash
cd auth-server

mvn spring-boot:run        # run (also builds frontend)
mvn package                # full build including Nuxt generate
mvn test                   # run tests only

# Frontend only (faster iteration):
cd frontend
npm install
npm run dev                # Nuxt dev server on :3000
npm run generate           # static export consumed by Maven
```

### web-client (Nuxt 4)

```bash
cd web-client
npm install
npm run dev                # dev server on :3000
npm run build              # SSR build
npm run generate           # static export
```

### Infrastructure

```bash
# Start the MySQL auth-server-db (requires MYSQL_AUTH_SERVER_ROOT_PASSWORD env var)
docker compose up -d auth-server-db
```

## Key Configuration

- `auth-server/src/main/resources/application.yml` — server port defaults to `${SERVER_PORT:9000}`
- All other services use `application.properties` with minimal config; most config is expected to come from `config-server`
- All services target **Java 21** and use **Spring Boot 4.0.5** with **Spring Cloud 2025.1.1**

## Database

Auth-server DB schema (MySQL 8, port 3307):
- `user_credential` — stores email, bcrypt password, and a `user_guid` UUID
- `role` — many roles per credential, linked by `credential_id`

SQL scripts to create tables and seed test data are in `auth-server/src/main/resources/initialize_db/`.
