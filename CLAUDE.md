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
| `account-management` | Spring Boot (Maven) | 8082 | Account CRUD resource server (integration-test-only endpoints so far) |
| `web-client` | Nuxt 4 / Vue 3 | 3000 | Standalone frontend |

**Startup order:** config-server → eureka-server → gateway-server → auth-server → bff-server → simple-resource-server → account-management → web-client.

### auth-server is special

It bundles a Nuxt frontend via Maven. The Maven build:
1. Installs Node.js and runs `npm install` + `npm run generate` in `frontend/`
2. Copies `frontend/.output/public` into Spring Boot's `src/main/resources/static`
3. `SpaController` (in `controller/`) forwards `/`, `/login`, and `/ott/login` to `index.html` for client-side routing

The `auth-server-db` MySQL instance runs on port **3307** (not the default 3306) and is defined in `docker-compose.yml`. DB schema is in `auth-server/src/main/resources/initialize_db/`.

**Required env vars at startup:** `MYSQL_AUTH_SERVER_ROOT_USERNAME` and `MYSQL_AUTH_SERVER_ROOT_PASSWORD` (no defaults). `MYSQL_AUTH_SERVER_DB_URL` defaults to `jdbc:mysql://localhost:3307/auth-server-db`. `SERVER_PORT` defaults to `9000`. `REMEMBER_ME_KEY` defaults to `dev-remember-me-key-change-in-prod` (change in production). `REMEMBER_ME_TOKEN_VALIDITY_SECONDS` defaults to `1209600` (14 days). `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD` are required for Gmail OTP/magic-link delivery (no defaults); use a Gmail App Password, not the account password. `WEB_CLIENT_LOCATION` (property `web-client.location`) defaults to `http://localhost:3000` and is used to hand off to web-client after magic-link email verification when no saved OAuth2 request exists.

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

**OTT storage:** the MFA one-time PIN uses the custom in-memory `InMemoryOneTimePinService` (lost on restart — dev/test only). The account-creation magic-link token uses Spring Security's `JdbcOneTimeTokenService`, persisted in the `one_time_tokens` table (survives restart).

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

#### Account Creation Flow

New users self-register via the Nuxt `/signup` page → `POST /api/accounts` (public; `permitAll`, CSRF disabled). Controller → service → repository:

1. `SignupForm.vue` validates name/email/password/confirm client-side (vee-validate + yup) and POSTs `{ name, email, password }` as JSON.
2. `AccountController.createAccount()` (`@ResponseStatus(CREATED)`) delegates to `UserCredentialService.createAccount()` (`@Transactional`).
3. `CreateAccountValidator` re-validates server-side (name ≤ 255; email contains `@`; password ≥ 8 with at least one uppercase, lowercase, and digit) — throws `InvalidRequestException` (**400**) on failure.
4. Duplicate email → `EmailAlreadyExistsException` (**409**); `UNIQUE(email)` is the race backstop.
5. Password hashed via the `PasswordEncoder` bean (`{bcrypt}`); `user_guid` from `UUID`; `is_mfa_enabled=true`, `is_email_verified=false`.
6. `UserCredentialRepository.insert()` inserts the credential (id via `GeneratedKeyHolder`); `RoleRepository.insert()` adds a default `member` role.
7. Returns **201** `CreateAccountResponse(name, email)`.

**Auto-login + email verification (magic link).** Instead of making the new user re-type credentials, the signup page auto-starts the authorization-code flow, which routes the unverified account through magic-link email verification:

8. After the `201`, `SignupForm.vue` auto-submits a hidden native `<form method=post action=/login>` with the same `email`+`password` (param names `email`/`password`; CSRF disabled) — the browser navigates into Spring's form-login pipeline.
9. `MfaAwareDaoAuthenticationProvider` validates the password, sees `is_email_verified=false`, returns `CreateAccountPendingAuthenticationToken`.
10. `MfaRedirectAuthenticationSuccessHandler` builds an absolute `…/magic-link/login?magicLinkToken=<otp>` URL (`ServletUriComponentsBuilder.fromCurrentContextPath()`, request thread), emails it via `EmailService.sendMagicLinkEmail(to, magicLink)`, and redirects to `/signup/success`.
11. User clicks the link → `GET /magic-link/login?magicLinkToken=<otp>`. `SpaController.forwardMagicLinkSent` binds the token (`@RequestParam`) and **stores it in the HTTP session** (attribute `magicLinkToken`) before forwarding to the Nuxt page. This is deliberate: the page is a statically-generated SSR Nuxt route, and Vue Router strips the query string during client-side hydration (`window.location.search` is empty by the time any component hook runs), so the browser can't read the token — but the server sees it reliably on this GET. The token never needs to round-trip through client JS.
12. The Nuxt `magic-link/login` page renders a **"Continue with login"** button (a native `<form method="post" action="/magic-link/login">`; no client-side token reading, no auto-submit). The user clicks it → `POST /magic-link/login` carrying **no token field**. `SpaController.verifyMagicLink` reads `magicLinkToken` back **from the session**, consumes it via `JdbcOneTimeTokenService` (a delete-and-return against the `one_time_tokens` table) and verifies the returned `username` matches the pending account's email, removes the session attribute, calls `UserCredentialService.verifyEmail` (`is_email_verified=true`), builds a full `MfaAuthenticationToken`, saves the session, and redirects to the saved OAuth2 request. (A missing session attribute or a token that fails to consume → `redirect:/magic-link/login?error=invalidToken`.)
13. If no SavedRequest exists (direct `/signup` visit), `verifyMagicLink` redirects to the web-client base URL (`web-client.location`, default `http://localhost:3000`) so web-client restarts `/oauth2/authorize` with its own `state` (the auth-server can't initiate it — web-client validates `state` against its own `sessionStorage`). Limitation: same-browser only — the pending token, the saved request, **and now the captured `magicLinkToken`** all live in that one HTTP session. Requiring a button click (rather than auto-submitting on mount) means an email client/scanner that only prefetches the link cannot trigger the consuming POST.

| Class | Role |
|---|---|
| `AccountController` (`controller/`) | `POST /api/accounts` → `UserCredentialService.createAccount` |
| `CreateAccountValidator` (`service/`) | Server-side field validation; throws `InvalidRequestException` |
| `RoleRepository` (`repository/`) | `insert(credentialId, roleName)` — role row with a generated `role_guid` |
| `MfaAwareDaoAuthenticationProvider` (`component/`) | Unverified email → `CreateAccountPendingAuthenticationToken` |
| `CreateAccountPendingAuthenticationToken` (`principal/`) | First factor passed, email unverified; `isAuthenticated()=false` |
| `SpaController.forwardMagicLinkSent` (`controller/`) | `GET /magic-link/login` — captures `magicLinkToken` from the query into the session (the SPA can't read it post-hydration), forwards to the page |
| `SpaController.verifyMagicLink` (`controller/`) | `POST /magic-link/login` — reads `magicLinkToken` from the session, consumes it, verifies email, upgrades session, redirects to saved request or web-client |
| `InvalidRequestException` / `EmailAlreadyExistsException` (`exception/`) | Mapped to 400 / 409 by `GlobalExceptionHandler` (`@RestControllerAdvice`) |

`UserCredentialService` also gains `createAccount(request)`, `verifyEmail(email)`, and `isEmailVerified(email)`; `UserCredentialRepository` gains `insert(...)` and `verifyEmail(...)`; and the `UserCredential` record gains a `name` field (its `RowMapper` and `findByEmail` query are updated accordingly).

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

**Registered client — INTEGRATION_TEST_CLIENT:**

| Property | Value |
|---|---|
| `clientId` | `INTEGRATION_TEST_CLIENT` |
| `clientSecret` | `{noop}integration-test-secret` (seeded in `create_client_table.sql`) |
| `redirectUri` / `post_logout_redirect_uri` | none (machine-to-machine) |
| `scopes` | `INTEGRATION_TEST_CLIENT_READ`, `INTEGRATION_TEST_CLIENT_WRITE`, `INTEGRATION_TEST_CLIENT_UPDATE`, `INTEGRATION_TEST_CLIENT_DELETE` |
| `grantTypes` | `client_credentials` |

Used only by integration tests to obtain an access token for calling auth-server's own protected test endpoints (below).

#### auth-server as an OAuth2 Resource Server (integration-test self-calls)

In addition to being an Authorization Server, auth-server is **also an OAuth2 Resource Server** (`spring-boot-starter-oauth2-resource-server`). This exists so integration tests can drive flows that normally depend on reading an email (the MFA OTT and the account-creation magic link) without an inbox: a test authenticates as `INTEGRATION_TEST_CLIENT` via `client_credentials`, then uses that access token to call test-only endpoints on auth-server itself that return the generated token value directly.

How it is wired in `config/SecurityConfig.java`:
- `@EnableMethodSecurity` enables `@PreAuthorize` on controller methods.
- The default `SecurityFilterChain` adds `oauth2ResourceServer(jwt)`. Because that chain pins an explicit `AuthenticationManager` (for form-login / remember-me / guest), the resource server's `BearerTokenAuthenticationFilter` delegates bearer-token authentication to that same `ProviderManager`. A `JwtAuthenticationProvider` (built from the in-memory `jwtDecoder`) is therefore added to the `ProviderManager` explicitly — without it, a bearer request fails with `ProviderNotFoundException: No AuthenticationProvider found for BearerTokenAuthenticationToken`.
- The `JwtAuthenticationConverter` maps the JWT `scope` claim to authorities with **no** prefix (mirroring simple-resource-server), so scope `INTEGRATION_TEST_CLIENT_WRITE` becomes the authority `INTEGRATION_TEST_CLIENT_WRITE`.

**Test-only endpoints (`MfaController`)** — each guarded by `@PreAuthorize("hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')")`, i.e. callable only with an `INTEGRATION_TEST_CLIENT` client_credentials access token:

| Endpoint | Purpose |
|---|---|
| `POST /ott/generate/test` | Like `/ott/generate` but returns the OTT value in the response body (still reads the pending user from the session) |
| `POST /magic-link/generate/test?email=<email>` | Mints an account-creation magic-link token via `JdbcOneTimeTokenService` for the given `email` and returns the token value; stateless — the email is passed explicitly rather than read from the session, since a client_credentials caller has no browser session |

**Self-testing pattern (client_credentials → call auth-server):**
1. `OAuth2Client.getClientCredentialsToken(...)` exchanges `INTEGRATION_TEST_CLIENT` credentials at `POST /oauth2/token` (`grant_type=client_credentials`) for an access token.
2. The test attaches that token as a `Bearer` header when calling a `/…/test` endpoint to obtain the OTT / magic-link token value directly.
3. The token value then feeds the normal verification endpoint (e.g. `POST /magic-link/login`) to complete the flow.

`CreateAccountIntegrationTest` exercises the full chain: create account → auto-login lands on `/signup/success` → client_credentials token → `POST /magic-link/generate/test` → `POST /magic-link/login` → lands on the web-client callback with an authorization code. See `auth-server/README.md` for live-server run instructions.

**Per-test HTTP client lifecycle:** the integration test classes extend `IntegrationTestBase`, which builds a fresh `AuthServerClient` + `OAuth2Client` in `@BeforeEach` and `close()`s them in `@AfterEach` (both are `AutoCloseable`; `AuthServerClient.close()` shuts down both its cookie-bearing and cookie-less `HttpClient`s). This replaced an earlier design where the clients were shared singleton `@Bean`s in `TestConfig`. *Before:* the cached Spring test context shared one `HttpClient` (one connection pool) across the whole suite; on long runs an idle pooled keep-alive connection outlived Tomcat's 20 s `keepAliveTimeout`, the server closed it, the client reused the dead connection, and the request failed with `ClosedChannelException` surfaced as `ConnectException` (the *second* test class to run failed; each passed in isolation). *After:* a fresh client per test means a fresh connection pool per test — no connection is idle long enough to be reaped, none is shared across tests, and each test gets a clean session. `TestConfig` is now an empty `@Configuration` that only anchors `@TestPropertySource`/`@Value`. **Do not** reintroduce shared client beans.

### web-client vs auth-server/frontend

- `web-client/` — standalone Nuxt 4 app, developed and deployed independently
- `auth-server/frontend/` — Nuxt app embedded inside auth-server's Spring Boot JAR via Maven build

### auth-server/frontend structure

```
auth-server/frontend/
├── app.vue                          # root layout wrapper
├── components/
│   ├── LoginForm.vue                # contains two forms: #login-form (POST /login — email, password, remember-me; intercepted by Spring Security's UsernamePasswordAuthenticationFilter) and #guest-form (POST /login/guest — no fields); Vuetify inputs use the HTML `form` attribute to bind to the correct form; "Continue as Guest" button submits #guest-form; also links to /signup ("Create an account")
│   ├── OttLoginForm.vue             # native HTML form (POST /ott/login); OTT text field + "Remember this browser?" checkbox (posts rememberBrowser=true); submitted after user receives their one-time token
│   └── SignupForm.vue               # account-creation form (name, email, password, confirm password); validated with vee-validate + yup; on submit fetch-POSTs { name, email, password } to /api/accounts; on 201 auto-submits a hidden native form (POST /login) with the same email+password to start the login flow (no credential re-entry); shows an inline v-alert on failure; links to /login
├── pages/
│   ├── login.vue                    # /login — mounts LoginForm centered on page
│   ├── about.vue                    # /about
│   ├── signup/
│   │   ├── index.vue                # /signup — mounts SignupForm centered on page
│   │   └── success.vue              # /signup/success — "Account creation succeeded. Please check your email to continue." (reachable by direct URL; no route guard)
│   ├── magic-link/
│   │   └── login.vue                # /magic-link/login — renders a "Continue with login" button (native form POST /magic-link/login, no token field); the token is captured server-side into the session on GET, so the page reads nothing from the URL (Vue Router strips the query on hydration)
│   └── ott/
│       └── login.vue                # /ott/login — on mount calls POST /ott/generate to trigger OTT delivery; mounts OttLoginForm centered on page
├── nuxt.config.ts
└── package.json
```

UI uses **Vuetify 4** (`vuetify-nuxt-module`). `/` redirects to `/login` via `routeRules`. The signup form validates client-side with **vee-validate** + **yup** (`@vee-validate/yup`).

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
│   │   ├── home.vue                     # 2×2 card grid + 5th wide card (md="8"); does NOT auto-authenticate (onBeforeMount is empty) — authorization is initiated manually via the RoleApiCard "authorize" button
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

### account-management

A second **OAuth2 Resource Server** (Spring Security 7.x, port `8082`), wired the same way as simple-resource-server: it validates JWT bearer tokens issued by auth-server, runs `anyRequest().permitAll()` at the filter chain level, and enforces access purely via `@PreAuthorize` (`@EnableMethodSecurity`). `config/SecurityConfig.java` defines the `SecurityFilterChain`, a delegating `PasswordEncoder` bean (`{bcrypt}`), and a `JwtAuthenticationConverter` that maps the JWT `scope` claim to authorities with **no** prefix and the `roles` claim to `ROLE_*`.

Unlike simple-resource-server, it owns no schema of its own — it reads and writes the **shared auth-server DB** (`user_credential` and `role` tables, MySQL on port `3307`) directly via `JdbcTemplate`. It uses the same `MYSQL_AUTH_SERVER_*` env vars as auth-server (`MYSQL_AUTH_SERVER_DB_URL` defaults to `jdbc:mysql://localhost:3307/auth-server-db`; `MYSQL_AUTH_SERVER_ROOT_USERNAME`/`MYSQL_AUTH_SERVER_ROOT_PASSWORD` required, no defaults). JWK URI defaults to `${AUTH_SERVER_JWK_URI:http://localhost:9000/oauth2/jwks}` (fetched lazily on first authenticated request). Config lives in `src/main/resources/application.yaml`.

**Integration-test-only endpoints (`controller/AccountController`)** under `/api/account`, each callable only with an `INTEGRATION_TEST_CLIENT` `client_credentials` access token (the same machine client auth-server seeds in `create_client_table.sql`). These exist so integration tests across the stack can create and tear down accounts directly in the shared DB without driving the full signup/email flow:

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /api/account/test` | `@PreAuthorize hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')` | Creates an account with caller-supplied `mfaEnabled`/`emailVerified`/`roles`; returns **201** `CreateAccountResponse(name, email, userGUID, mfaEnabled, emailVerified, roles)` |
| `DELETE /api/account/test?email=…` *or* `?userGUID=…` | `@PreAuthorize hasAuthority('INTEGRATION_TEST_CLIENT_DELETE')` | Deletes by **exactly one** of email/userGUID; returns **204**. Idempotent — no match is a no-op so teardown can run repeatedly |

Request/response and flow details:
- `dto/request/CreateAccountRequest(name, email, password, mfaEnabled, emailVerified, roles)` — the compact constructor defaults `mfaEnabled=true` and `emailVerified=false` when omitted/null (Jackson uses the canonical constructor).
- `validator/Validator` re-validates server-side: name required and ≤ 255; email required and contains `@`; password required, ≥ 8 chars with at least one uppercase, lowercase, and digit → `InvalidRequestException` (**400**). For delete it enforces exactly one of email/userGUID (not both, not neither) → also **400**.
- `service/AccountService.createTestAccount()` (`@Transactional`) rejects a duplicate email with `EmailAlreadyExistsException` (**409**), hashes the password via the `PasswordEncoder`, generates a `user_guid` UUID, inserts the credential (`UserCredentialRepository.insert`, id via `GeneratedKeyHolder`), and inserts roles. `resolveRoles()` always includes `MEMBER` (the floor) plus any requested roles, de-duplicated preserving order. `deleteTestAccount()` deletes role rows before the credential (the role FK has no `ON DELETE CASCADE`).
- `enums/Role` — `PASTOR`, `DEACON`, `SMALL_GROUP_LEADER`, `VICE_SMALL_GROUP_LEADER`, `MEMBER`, `GUEST`; serialized to/from its lowercase `value` (`@JsonValue`/`@JsonCreator`, case-insensitive), e.g. `member`, `small_group_leader`.
- `exception/GlobalExceptionHandler` (`@RestControllerAdvice`) maps `InvalidRequestException` → **400** and `EmailAlreadyExistsException` → **409**, both as `{"error": "<message>"}`.
- **Swagger UI** is available via `springdoc-openapi-starter-webmvc-ui`; the endpoints carry `@Operation`/`@Parameter` annotations.

**Integration tests (`src/test/java/.../integration/`)** require both auth-server and account-management running. `AccountLifecycleIntegrationTest` obtains a `client_credentials` token (scopes `INTEGRATION_TEST_CLIENT_WRITE INTEGRATION_TEST_CLIENT_DELETE`) from auth-server via `OAuth2Client`, then drives create→delete (by email, and by userGUID) against account-management via `AccountManagementClient` (cookie-less; Bearer-token only). `TestConfig` builds the two clients as beans from `auth-server-location`/`account-management-location` in `src/test/resources/application.yml`. CI boots both services and runs these tests (see below).

### account-management endpoints summary

| Endpoint | Required authority |
|---|---|
| `POST /api/account/test` | `INTEGRATION_TEST_CLIENT_WRITE` scope |
| `DELETE /api/account/test` | `INTEGRATION_TEST_CLIENT_DELETE` scope |

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

- `auth-server/src/main/resources/application.yml` — server port defaults to `${SERVER_PORT:9000}`; `MYSQL_AUTH_SERVER_ROOT_USERNAME` and `MYSQL_AUTH_SERVER_ROOT_PASSWORD` are required with no fallback; `MYSQL_AUTH_SERVER_DB_URL` defaults to `jdbc:mysql://localhost:3307/auth-server-db`; Gmail SMTP is configured under `spring.mail` — `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD` are required (no defaults); uses `smtp.gmail.com:587` with STARTTLS; `web-client.location` defaults to `http://localhost:3000` (override: `WEB_CLIENT_LOCATION`) — web-client hand-off target after magic-link verification when no saved request exists
- `simple-resource-server/src/main/resources/application.yml` — port defaults to `8081` (override: `SERVER_PORT`); JWK URI defaults to `http://localhost:9000/oauth2/jwks` (override: `AUTH_SERVER_JWK_URI`); CORS origin defaults to `http://localhost:3000` (override: `WEB_CLIENT_ORIGIN` via `web.client.origin` property)
- `web-client/nuxt.config.ts` — `runtimeConfig.public.simpleResourceServerUrl` defaults to `http://localhost:8081` (override: `NUXT_PUBLIC_SIMPLE_RESOURCE_SERVER_URL`); `runtimeConfig.public.authServerUrl` defaults to `http://localhost:9000` (override: `NUXT_PUBLIC_AUTH_SERVER_URL`); `runtimeConfig.public.webClientId` defaults to `WEB_CLIENT` (override: `NUXT_PUBLIC_WEB_CLIENT_ID`); `runtimeConfig.public.webClientSecret` has no default and **must** be set via `NUXT_PUBLIC_WEB_CLIENT_SECRET` (must match the `client_secret` stored in auth-server's `oauth2_registered_client` table)
- All other services use `application.properties` with minimal config; most config is expected to come from `config-server`
- All services target **Java 21** and use **Spring Boot 4.0.5** with **Spring Cloud 2025.1.1**

## CI / CD — GitHub Actions

Workflows live in `.github/workflows/`. CI workflows run on `pull_request` events `opened` and `synchronize`. CD workflows run on `push` to `main` (i.e. after a PR merges).

### auth-server-ci.yml — `paths: auth-server/**`

1. Starts a **MySQL 8 service container** (port 3306 inside CI, not 3307). `MYSQL_AUTH_SERVER_DB_URL` is overridden to `jdbc:mysql://localhost:3306/auth-server-db`.
2. Seeds the DB by running the scripts in `auth-server/src/main/resources/initialize_db/` in order: `create_authentication_tables.sql` → `create_client_table.sql` → `create_one_time_tokens_table.sql` → `initialize_test_users.sql`.
3. Builds with `mvn package -DskipTests` — builds the JAR and the embedded Nuxt frontend once.
4. Starts auth-server in the background with `java -jar`.
5. Polls `GET /actuator/health` until `UP` (150 s timeout).
6. Runs integration tests with `mvn surefire:test`.

**Required GitHub secrets:** `MYSQL_AUTH_SERVER_ROOT_USERNAME` (set to `root`), `MYSQL_AUTH_SERVER_ROOT_PASSWORD`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`.

**`auth-server/frontend/package-lock.json` is gitignored.** It was removed from version control to prevent platform-specific native binary mismatches (Windows-generated lockfiles don't include Linux binaries required in CI). The `frontend-maven-plugin` regenerates it on each build for the current platform.

**`WEB_CLIENT` client secret** in `create_client_table.sql` is seeded as `{noop}secret`, matching `web-client-secret` in `src/test/resources/application.yml`.

### auth-server-cd.yml — `paths: auth-server/**`

Triggers on push to `main`. Skipped automatically when the commit message contains `[skip ci]` (used by the version-bump bot commit to prevent a loop).

1. Reads the current `<version>` from `auth-server/pom.xml` (e.g. `0.0.1-SNAPSHOT`).
2. Strips `-SNAPSHOT` and increments the patch digit to produce the **release version** (e.g. `0.0.2`).
3. Sets `pom.xml` to the release version with `mvn versions:set`.
4. Builds and pushes the Docker image via `mvn jib:build -DskipTests` — base image `eclipse-temurin:21-jre`; pushes two tags: `<release-version>` and `latest` (e.g. `yourname/auth-server:0.0.2` and `yourname/auth-server:latest`).
5. Sets `pom.xml` to the next SNAPSHOT (e.g. `0.0.2-SNAPSHOT`) and commits it back to `main` as `github-actions[bot]` with `[skip ci]` in the message.

**Required GitHub secrets:** `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`.

**Required one-time repo setup:**
- Settings → Actions → General → Workflow permissions → **Read and write permissions**
- Settings → Branches → main protection rule → Allow specified actors to bypass required pull requests → add **GitHub Actions**

### simple-resource-server-ci.yml — `paths: simple-resource-server/**`

Runs `mvn test`, which executes `contextLoads()` in `SimpleResourceServerApplicationTests`. No external services are needed — the JWK set is fetched lazily (on the first authenticated request, not at startup), so auth-server does not need to be running.

### account-management-ci.yml — `paths: account-management/src/**`, `account-management/pom.xml`

Runs the integration tests against **both** live services. It (1) starts a MySQL 8 service container (port 3306) and seeds the full auth-server schema from `auth-server/src/main/resources/initialize_db/` (so the `user_credential`/`role` tables exist and `INTEGRATION_TEST_CLIENT` is seeded), (2) builds and starts **auth-server** (`mvn package -DskipTests` → `java -jar`) and waits for its health endpoint, (3) builds and starts **account-management** the same way (the `package` step also compiles the test sources) and waits for its health endpoint, then (4) runs `mvn surefire:test` — a failing integration test fails the job. Because auth-server is booted here, its mail config must be present. **Required secrets:** `MYSQL_AUTH_SERVER_ROOT_USERNAME` (= `root`), `MYSQL_AUTH_SERVER_ROOT_PASSWORD`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`.

### account-management-cd.yml — `paths: account-management/src/**`, `account-management/pom.xml`

Triggers on push to `main`; skips its own version-bump commit via `[skip ci]`. Same pattern as auth-server-cd: read `<version>` from `pom.xml`, strip `-SNAPSHOT` and bump the patch to the release version, `mvn versions:set`, build and push the Docker image via `mvn jib:build -DskipTests` (base `eclipse-temurin:21-jre`; tags `<release-version>` and `latest`), then set the next `-SNAPSHOT` and commit it back to `main` as `github-actions[bot]`. Checkout/push use a `GH_PAT`. **Required secrets:** `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `GH_PAT`.

## Database

Auth-server DB schema (MySQL 8, port 3307). The `account-management` service shares this same DB and the `user_credential`/`role` tables (it has no schema of its own):
- `user_credential` — stores `email`, `name`, bcrypt `password`, `user_guid` UUID, `is_mfa_enabled` (boolean, default `true`), and `is_email_verified` (boolean, default `false`)
- `role` — many roles per credential, linked by `credential_id`; the role FK has no `ON DELETE CASCADE`, so account-management deletes role rows before the credential
- `one_time_tokens` — Spring Security `JdbcOneTimeTokenService` schema (`token_value` PK, `username`, `expires_at`); backs the account-creation magic-link token. Standalone (no FK); `username` holds the user's email. Rows are deleted on consume; expired-but-unclicked rows are not cleaned up (dev/test scale)

SQL scripts to create tables and seed test data are in `auth-server/src/main/resources/initialize_db/`.
