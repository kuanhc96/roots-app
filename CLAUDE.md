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
| `simple-resource-server` | Spring Boot (Maven) | 8081 | Example protected resource with role endpoints |
| `web-client` | Nuxt 4 / Vue 3 | 3000 | Standalone frontend |

**Startup order:** config-server → eureka-server → gateway-server → auth-server → bff-server → simple-resource-server → web-client.

### auth-server is special

It bundles a Nuxt frontend via Maven. The Maven build:
1. Installs Node.js and runs `npm install` + `npm run generate` in `frontend/`
2. Copies `frontend/.output/public` into Spring Boot's `src/main/resources/static`
3. `SpaController` forwards `/` to `index.html` for client-side routing

The `auth-server-db` MySQL instance runs on port **3307** (not the default 3306) and is defined in `docker-compose.yml`. DB schema is in `auth-server/src/main/resources/initialize_db/`.

**Required env vars at startup:** `MYSQL_AUTH_SERVER_ROOT_USERNAME` and `MYSQL_AUTH_SERVER_ROOT_PASSWORD` (no defaults). `MYSQL_AUTH_SERVER_DB_URL` defaults to `jdbc:mysql://localhost:3307/auth-server-db`. `SERVER_PORT` defaults to `9000`. `WEB_CLIENT_REDIRECT_URI` defaults to `http://localhost:3000/callback`.

### Spring Security / OAuth2 Authorization Server

Auth-server runs as an **OAuth2 Authorization Server** (Spring Authorization Server 2.x, Spring Security 7.x). All security beans live in `config/SecurityConfig.java`.

- Form login uses `email` as the username field (not `username`)
- CORS and CSRF are disabled globally
- `SpaController` and Spring Security coexist on `/login`: GET `/login` is permitted through to the controller (forwards to Nuxt page); POST `/login` is intercepted by Spring Security's filter before MVC for credential processing
- `UserDetailsService` uses `JdbcUserDetailsManager` with custom queries against the `user_credential`/`role` schema; `email` is the lookup key
- JWK key pair is generated in-memory at startup (dev/test only — tokens are invalidated on restart)

**OAuth2 protocol endpoints:**

| Endpoint | Purpose |
|---|---|
| `GET /oauth2/authorize` | Start Authorization Code flow |
| `POST /oauth2/token` | Issue tokens |
| `GET /oauth2/jwks` | Public keys for token verification |
| `POST /oauth2/revoke` | Token revocation |
| `GET /connect/userinfo` | OIDC UserInfo |
| `GET /.well-known/openid-configuration` | OIDC discovery |

**Registered client — WEB_CLIENT:**

| Property | Value |
|---|---|
| `clientId` | `WEB_CLIENT` |
| `clientSecret` | `web-client.client-secret` in `application.yml` (stored as `{noop}<value>`) |
| `redirectUri` | `http://localhost:3000/callback` (override: `WEB_CLIENT_REDIRECT_URI`) |
| `scopes` | `openid`, `WEB_CLIENT_READ` |
| `grantTypes` | `authorization_code`, `refresh_token` |

Test the full Authorization Code flow with:
```
GET http://localhost:9000/oauth2/authorize?response_type=code&client_id=WEB_CLIENT&redirect_uri=http://localhost:3000/callback&scope=openid%20WEB_CLIENT_READ&state=state
```

### web-client vs auth-server/frontend

- `web-client/` — standalone Nuxt 4 app, developed and deployed independently
- `auth-server/frontend/` — Nuxt app embedded inside auth-server's Spring Boot JAR via Maven build

### auth-server/frontend structure

```
auth-server/frontend/
├── app.vue                          # root layout wrapper
├── components/
│   └── LoginForm.vue                # Vuetify card: email + password fields, login button
├── pages/
│   ├── login.vue                    # /login — mounts LoginForm centered on page
│   └── about.vue                    # /about
├── nuxt.config.ts
└── package.json
```

UI uses **Vuetify 4** (`vuetify-nuxt-module`). `/` redirects to `/login` via `routeRules`.

### web-client structure

```
web-client/
├── app/
│   ├── app.vue                          # root layout wrapper
│   ├── components/
│   │   ├── HomeCard.vue                 # reusable card (title, lorem ipsum, disabled button)
│   │   └── RoleApiCard.vue              # wide card with 6 role API buttons + response display
│   ├── composables/
│   │   └── useSimpleResourceClient.ts   # instantiates SimpleResourceClient from runtimeConfig
│   ├── pages/
│   │   ├── home.vue                     # 2×2 card grid + 5th wide card (md="8"); redirects unauthenticated users to /oauth2/authorize
│   │   └── callback.vue                 # OAuth2 callback; exchanges auth code for access token, stores in sessionStorage
│   └── utils/
│       └── SimpleResourceClient.ts      # axios-based client class for simple-resource-server
├── nuxt.config.ts
└── package.json
```

UI uses **Vuetify 4** (`vuetify-nuxt-module`). The home page grid is 12-column; regular cards are `md="4"`, the wide role card is `md="8"`.

### simple-resource-server endpoints

`RoleController` at `src/main/java/com/roots/simple_resource_server/RoleController.java` exposes six GET endpoints under `/api/role/`, each returning a plain `text/plain` string:

| Endpoint | Response |
|---|---|
| `/api/role/pastor` | `I am a pastor` |
| `/api/role/deacon` | `I am a deacon` |
| `/api/role/small-group-leader` | `I am a small group leader` |
| `/api/role/vice-small-group-leader` | `I am a vice small group leader` |
| `/api/role/member` | `I am a member` |
| `/api/role/guest` | `I am a guest` |

CORS is allowed from `http://localhost:3000` by default (overridable via `web.client.origin` property).

simple-resource-server is an **OAuth2 Resource Server** (Spring Security 7.x). It validates JWT bearer tokens issued by auth-server.

- JWK URI fetched from auth-server at `${AUTH_SERVER_JWK_URI:http://localhost:9000/oauth2/jwks}`
- `anyRequest().permitAll()` at the filter chain level; security is enforced purely via `@PreAuthorize` on each method
- `@EnableMethodSecurity` enables `@PreAuthorize` on each endpoint
- Protected endpoints require both `WEB_CLIENT_READ` (from the JWT `scope` claim) and the matching `ROLE_*` (from the JWT `roles` claim, prefixed with `ROLE_` by `JwtAuthenticationConverter`)
- `config/SecurityConfig.java` — `SecurityFilterChain` + `JwtAuthenticationConverter` (reads `scope` → `*` with no prefix, and `roles` → `ROLE_*`)

**Role → endpoint mapping:**

| Endpoint | Required role |
|---|---|
| `/api/role/pastor` | `ROLE_PASTOR` |
| `/api/role/deacon` | `ROLE_DEACON` |
| `/api/role/small-group-leader` | `ROLE_SMALL_GROUP_LEADER` |
| `/api/role/vice-small-group-leader` | `ROLE_VICE_SMALL_GROUP_LEADER` |
| `/api/role/member` | `ROLE_MEMBER` |
| `/api/role/guest` | _(none — public)_ |

The auth-server must include a `roles` claim in issued JWTs (uppercase values, e.g. `PASTOR`) for role checks to pass. This is implemented via `OAuth2TokenCustomizer` in `config/SecurityConfig.java`.

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

- `auth-server/src/main/resources/application.yml` — server port defaults to `${SERVER_PORT:9000}`; `WEB_CLIENT_SECRET`, `MYSQL_AUTH_SERVER_ROOT_USERNAME` and `MYSQL_AUTH_SERVER_ROOT_PASSWORD` are required with no fallback; `MYSQL_AUTH_SERVER_DB_URL` defaults to `jdbc:mysql://localhost:3307/auth-server-db`; `web-client.client-secret` is a placeholder secret for the registered OAuth2 client; `web-client.redirect-uri` defaults to `http://localhost:3000/callback` (override with `WEB_CLIENT_REDIRECT_URI`)
- `simple-resource-server/src/main/resources/application.yml` — port defaults to `8081` (override: `SERVER_PORT`); JWK URI defaults to `http://localhost:9000/oauth2/jwks` (override: `AUTH_SERVER_JWK_URI`); CORS origin defaults to `http://localhost:3000` (override: `WEB_CLIENT_ORIGIN` via `web.client.origin` property)
- `web-client/nuxt.config.ts` — `runtimeConfig.public.simpleResourceServerUrl` defaults to `http://localhost:8081` (override: `NUXT_PUBLIC_SIMPLE_RESOURCE_SERVER_URL`); `runtimeConfig.public.authServerUrl` defaults to `http://localhost:9000` (override: `NUXT_PUBLIC_AUTH_SERVER_URL`); `runtimeConfig.public.webClientId` defaults to `WEB_CLIENT` (override: `NUXT_PUBLIC_WEB_CLIENT_ID`); `runtimeConfig.public.webClientSecret` has no default and **must** be set via `NUXT_PUBLIC_WEB_CLIENT_SECRET` (same value as auth-server's `WEB_CLIENT_SECRET`)
- All other services use `application.properties` with minimal config; most config is expected to come from `config-server`
- All services target **Java 21** and use **Spring Boot 4.0.5** with **Spring Cloud 2025.1.1**

## Database

Auth-server DB schema (MySQL 8, port 3307):
- `user_credential` — stores email, bcrypt password, and a `user_guid` UUID
- `role` — many roles per credential, linked by `credential_id`

SQL scripts to create tables and seed test data are in `auth-server/src/main/resources/initialize_db/`.
