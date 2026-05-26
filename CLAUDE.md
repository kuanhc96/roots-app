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
3. `SpaController` (in `controller/`) forwards `/`, `/login`, and `/ott/login` to `index.html` for client-side routing

The `auth-server-db` MySQL instance runs on port **3307** (not the default 3306) and is defined in `docker-compose.yml`. DB schema is in `auth-server/src/main/resources/initialize_db/`.

**Required env vars at startup:** `MYSQL_AUTH_SERVER_ROOT_USERNAME` and `MYSQL_AUTH_SERVER_ROOT_PASSWORD` (no defaults). `MYSQL_AUTH_SERVER_DB_URL` defaults to `jdbc:mysql://localhost:3307/auth-server-db`. `SERVER_PORT` defaults to `9000`. `REMEMBER_ME_KEY` defaults to `dev-remember-me-key-change-in-prod` (change in production). `REMEMBER_ME_TOKEN_VALIDITY_SECONDS` defaults to `1209600` (14 days). `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD` are required for Gmail OTP delivery (no defaults); use a Gmail App Password, not the account password.

### Spring Security / OAuth2 Authorization Server

Auth-server runs as an **OAuth2 Authorization Server** (Spring Authorization Server 2.x, Spring Security 7.x). All security beans live in `config/SecurityConfig.java`.

- Form login uses `email` as the username field (not `username`)
- CORS and CSRF are disabled globally
- `SpaController` and Spring Security coexist on `/login`: GET `/login` is permitted through to the controller (forwards to Nuxt page); POST `/login` is intercepted by Spring Security's filter before MVC for credential processing
- `UserDetailsService` uses `JdbcUserDetailsManager` with custom queries against the `user_credential`/`role` schema; `email` is the lookup key
- Remember-me is opt-in: the login form posts `remember-me=true` when the checkbox is checked; `TokenBasedRememberMeServices` (SHA-256, `alwaysRemember=false`) only issues the cookie when that parameter is present
- JWK key pair is generated in-memory at startup (dev/test only — tokens are invalidated on restart)
- All requests use `anyRequest().permitAll()` at the filter chain level; MFA step enforcement is handled implicitly by requiring a `MfaPendingAuthenticationToken` in the session for OTT generation and verification

#### MFA Flow

MFA is **optional per user**, controlled by `is_mfa_enabled` in the `user_credential` table (default `true`). Each authentication provider checks this flag after validating the first factor and returns either a pending or fully authenticated token accordingly.

**Authentication tokens (`principal/`):**

| Class | Purpose |
|---|---|
| `MfaPendingAuthenticationToken` | Issued after first-factor success when MFA is enabled; `isAuthenticated() = false`, no granted authorities; held in the HTTP session until OTT is verified |
| `MfaAuthenticationToken` | Fully authenticated token; issued directly when MFA is disabled, or after OTT verification when MFA is enabled; authorities are wrapped in `FactorGrantedAuthority` (required for OIDC logout in Spring Security 7) |
| `GuestAuthenticationToken` | Unauthenticated marker token submitted by `POST /login/guest`; no credentials or authorities; consumed by `GuestAuthenticationProvider` |

**Authentication providers (`component/`):**

| Class | Replaces | Behaviour |
|---|---|---|
| `MfaAwareDaoAuthenticationProvider` | `DaoAuthenticationProvider` | Validates username/password; checks `UserCredentialService.isMfaEnabled()` — returns `MfaPendingAuthenticationToken` if MFA is on, `MfaAuthenticationToken` if off |
| `MfaAwareRememberMeAuthenticationProvider` | `RememberMeAuthenticationProvider` | Delegates to the parent to validate the remember-me cookie; same conditional MFA logic |
| `GuestAuthenticationProvider` | _(new)_ | Supports `GuestAuthenticationToken`; creates a synthetic `UserDetails` with username `"guest"` and authority `"GUEST"`; returns a fully authenticated `MfaAuthenticationToken` — no password or MFA check |

**Success handler (`component/MfaRedirectAuthenticationSuccessHandler`):**  
Extends `SavedRequestAwareAuthenticationSuccessHandler`. If the resulting token is a `MfaPendingAuthenticationToken`, redirects to `/ott/login`; otherwise calls `super.onAuthenticationSuccess()` for the standard saved-request redirect. Wired into both `UsernamePasswordAuthenticationFilter` and `RememberMeAuthenticationFilter`.

**Logout handler (`component/RememberMeOidcLogoutAuthenticationSuccessHandler`):**  
Implements `AuthenticationSuccessHandler`. Clears the `remember-me` cookie (setting `Max-Age=0`) before delegating to `OidcLogoutAuthenticationSuccessHandler`. Wired into the authorization server's OIDC logout endpoint via `SecurityConfig`.

**MFA endpoints:**

| Endpoint | Handler | Purpose |
|---|---|---|
| `GET /ott/login` | `SpaController` | Forwards to the Nuxt OTT login page |
| `POST /ott/generate` | `MfaController` | Generates a one-time token for the pending user, logs it to stdout, and emails it to the user via `EmailService` |
| `POST /ott/login` | `SpaController` | Verifies the submitted OTT; if `rememberBrowser=true` is posted, disables MFA for the user; upgrades the session to `MfaAuthenticationToken` and redirects to the original OAuth2 request |

**Guest login endpoint:**

| Endpoint | Handler | Purpose |
|---|---|---|
| `POST /login/guest` | `SpaController` | Authenticates the user as guest (no credentials required); creates a `GuestAuthenticationToken`, delegates to `GuestAuthenticationProvider` via `AuthenticationManager`, saves the resulting `MfaAuthenticationToken` to the session, and redirects to the saved OAuth2 request |

**OTT storage:** `InMemoryOneTimeTokenService` (Spring Security built-in). Tokens are lost on restart — dev/test only.

**Supporting classes (`model/`, `repository/`, `service/`):**

| Class | Role |
|---|---|
| `UserCredential` | Record mapping a `user_credential` row |
| `UserCredentialRepository` | JdbcTemplate-based repo; `findByEmail` and `setMfaEnabled` |
| `UserCredentialService` | `isMfaEnabled(email)` and `disableMfa(email)`; injected into both auth providers and `SpaController` |
| `EmailService` | `sendOTTEmail(to, ott)` — sends the OTT to the user's email via Gmail SMTP (`JavaMailSender`); injected into `MfaController` |

**Full MFA flow (MFA enabled):**
1. User submits credentials or browser sends remember-me cookie.
2. The appropriate MFA-aware provider validates the first factor, checks `is_mfa_enabled = true`, and stores a `MfaPendingAuthenticationToken` in the session.
3. `MfaRedirectAuthenticationSuccessHandler` detects the pending token and redirects to `/ott/login`.
4. The Nuxt `/ott/login` page mounts and immediately calls `POST /ott/generate`, which prints the OTT to the server console and emails it to the user via `EmailService`.
5. User enters the OTT and optionally checks "Remember this browser?", then submits `POST /ott/login`.
6. `SpaController.verifyOtt()` consumes the token. If `rememberBrowser=true`, it calls `UserCredentialService.disableMfa()` to set `is_mfa_enabled = false`. The session is upgraded to `MfaAuthenticationToken` and redirected to the saved OAuth2 authorization request.

**MFA disabled flow:**
1. Provider validates first factor, checks `is_mfa_enabled = false`, and returns `MfaAuthenticationToken` directly.
2. `MfaRedirectAuthenticationSuccessHandler` sees a non-pending token and falls through to the saved-request redirect — no OTT step.

**OAuth2 protocol endpoints:**

| Endpoint | Purpose |
|---|---|
| `GET /oauth2/authorize` | Start Authorization Code flow |
| `POST /oauth2/token` | Issue tokens |
| `GET /oauth2/jwks` | Public keys for token verification |
| `POST /oauth2/revoke` | Token revocation |
| `GET /connect/userinfo` | OIDC UserInfo |
| `GET /connect/logout` | OIDC RP-Initiated Logout |
| `GET /.well-known/openid-configuration` | OIDC discovery |

**Registered client — WEB_CLIENT:**

| Property | Value |
|---|---|
| `clientId` | `WEB_CLIENT` |
| `clientSecret` | stored in `oauth2_registered_client` DB table |
| `redirectUri` | stored in `oauth2_registered_client` DB table (default seed: `http://localhost:3000/callback`) |
| `post_logout_redirect_uri` | stored in `oauth2_registered_client` DB table (default seed: `http://localhost:3000/logout`) |
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
│   ├── LoginForm.vue                # contains two forms: #login-form (POST /login — email, password, remember-me; intercepted by Spring Security's UsernamePasswordAuthenticationFilter) and #guest-form (POST /login/guest — no fields); Vuetify inputs use the HTML `form` attribute to bind to the correct form; "Continue as Guest" button submits #guest-form
│   └── OttLoginForm.vue             # native HTML form (POST /ott/login); OTT text field + "Remember this browser?" checkbox (posts rememberBrowser=true); submitted after user receives their one-time token
├── pages/
│   ├── login.vue                    # /login — mounts LoginForm centered on page
│   ├── about.vue                    # /about
│   └── ott/
│       └── login.vue                # /ott/login — on mount calls POST /ott/generate to trigger OTT delivery; mounts OttLoginForm centered on page
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
│   │   └── RoleApiCard.vue              # wide card with 6 role API buttons + response display; authorize button triggers OAuth2 Authorization Code flow if no valid access token; logout button triggers OIDC RP-Initiated Logout (clears sessionStorage, redirects to /connect/logout with id_token_hint and post_logout_redirect_uri)
│   ├── composables/
│   │   ├── useOAuth.ts                  # authorize() — refreshes existing tokens or initiates a new Authorization Code flow; extracted from RoleApiCard.vue for reuse across pages
│   │   └── useSimpleResourceClient.ts   # instantiates SimpleResourceClient from runtimeConfig
│   ├── pages/
│   │   ├── home.vue                     # 2×2 card grid + 5th wide card (md="8"); redirects unauthenticated users to /oauth2/authorize
│   │   ├── callback.vue                 # OAuth2 callback; exchanges auth code for access token, stores in sessionStorage
│   │   └── logout.vue                   # post-logout landing page; clears sessionStorage tokens on mount; shows authorize button to re-authenticate
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
| `/api/role/guest` | `WEB_CLIENT_READ` scope (no role required) |

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

#### Integration tests (require live auth-server)

Integration tests under `src/test/java/com/roots/authserver/integration/` hit a running auth-server at `localhost:9000`. Start MySQL and auth-server first, then:

```bash
mvn test -Dtest="GuestLoginIntegrationTest"   # guest login OAuth2 flow
mvn test -Dtest="*IntegrationTest"            # all integration tests
```

Connection targets are configured in `src/test/resources/application.yml` (`auth-server-location`, `web-client-location`, `web-client-secret`). Override on the command line with `-D<property>=<value>`.

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

- `auth-server/src/main/resources/application.yml` — server port defaults to `${SERVER_PORT:9000}`; `MYSQL_AUTH_SERVER_ROOT_USERNAME` and `MYSQL_AUTH_SERVER_ROOT_PASSWORD` are required with no fallback; `MYSQL_AUTH_SERVER_DB_URL` defaults to `jdbc:mysql://localhost:3307/auth-server-db`; Gmail SMTP is configured under `spring.mail` — `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD` are required (no defaults); uses `smtp.gmail.com:587` with STARTTLS
- `simple-resource-server/src/main/resources/application.yml` — port defaults to `8081` (override: `SERVER_PORT`); JWK URI defaults to `http://localhost:9000/oauth2/jwks` (override: `AUTH_SERVER_JWK_URI`); CORS origin defaults to `http://localhost:3000` (override: `WEB_CLIENT_ORIGIN` via `web.client.origin` property)
- `web-client/nuxt.config.ts` — `runtimeConfig.public.simpleResourceServerUrl` defaults to `http://localhost:8081` (override: `NUXT_PUBLIC_SIMPLE_RESOURCE_SERVER_URL`); `runtimeConfig.public.authServerUrl` defaults to `http://localhost:9000` (override: `NUXT_PUBLIC_AUTH_SERVER_URL`); `runtimeConfig.public.webClientId` defaults to `WEB_CLIENT` (override: `NUXT_PUBLIC_WEB_CLIENT_ID`); `runtimeConfig.public.webClientSecret` has no default and **must** be set via `NUXT_PUBLIC_WEB_CLIENT_SECRET` (must match the `client_secret` stored in auth-server's `oauth2_registered_client` table)
- All other services use `application.properties` with minimal config; most config is expected to come from `config-server`
- All services target **Java 21** and use **Spring Boot 4.0.5** with **Spring Cloud 2025.1.1**

## CI / GitHub Actions

Workflows live in `.github/workflows/`. Each runs on `pull_request` events `opened` and `synchronize`, filtered to the relevant service path.

### auth-server-ci.yml — `paths: auth-server/**`

1. Starts a **MySQL 8 service container** (port 3306 inside CI, not 3307). `MYSQL_AUTH_SERVER_DB_URL` is overridden to `jdbc:mysql://localhost:3306/auth-server-db`.
2. Seeds the DB by running the scripts in `auth-server/src/main/resources/initialize_db/` in order: `create_authentication_tables.sql` → `create_client_table.sql` → `initialize_test_users.sql`.
3. Builds with `mvn package -DskipTests` — builds the JAR and the embedded Nuxt frontend once.
4. Starts auth-server in the background with `java -jar`.
5. Polls `GET /actuator/health` until `UP` (150 s timeout).
6. Runs integration tests with `mvn surefire:test`.

**Required GitHub secrets:** `MYSQL_AUTH_SERVER_ROOT_USERNAME` (set to `root`), `MYSQL_AUTH_SERVER_ROOT_PASSWORD`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`.

**`auth-server/frontend/package-lock.json` is gitignored.** It was removed from version control to prevent platform-specific native binary mismatches (Windows-generated lockfiles don't include Linux binaries required in CI). The `frontend-maven-plugin` regenerates it on each build for the current platform.

**`WEB_CLIENT` client secret** in `create_client_table.sql` is seeded as `{noop}secret`, matching `web-client-secret` in `src/test/resources/application.yml`.

### simple-resource-server-ci.yml — `paths: simple-resource-server/**`

Runs `mvn test`, which executes `contextLoads()` in `SimpleResourceServerApplicationTests`. No external services are needed — the JWK set is fetched lazily (on the first authenticated request, not at startup), so auth-server does not need to be running.

## Database

Auth-server DB schema (MySQL 8, port 3307):
- `user_credential` — stores `email`, bcrypt `password`, `user_guid` UUID, `is_mfa_enabled` (boolean, default `true`), and `is_email_verified` (boolean, default `false`)
- `role` — many roles per credential, linked by `credential_id`

SQL scripts to create tables and seed test data are in `auth-server/src/main/resources/initialize_db/`.
