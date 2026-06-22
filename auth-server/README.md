# auth-server

A fullstack Spring Boot + Nuxt/Vue application that handles authentication for the roots-app platform. It embeds the Nuxt frontend as a static SPA served by Spring Boot, and manages user credentials and roles in a MySQL database.

## Environment Variables

| Variable                          | Required | Default | Description                     |
|-----------------------------------|---|---|---------------------------------|
| `MYSQL_AUTH_SERVER_ROOT_USERNAME` | Yes | — | MySQL username                  |
| `MYSQL_AUTH_SERVER_ROOT_PASSWORD` | Yes | — | MySQL password                  |
| `MYSQL_AUTH_SERVER_DB_URL`        | No | `jdbc:mysql://localhost:3307/auth-server-db` | JDBC connection URL             |
| `SERVER_PORT`                     | No | `9000` | HTTP port the server listens on |
| `REMEMBER_ME_KEY`                 | No | `dev-remember-me-key-change-in-prod` | Secret key used to sign remember-me cookies; change in production |
| `REMEMBER_ME_TOKEN_VALIDITY_SECONDS` | No | `1209600` (14 days) | Lifetime of the remember-me cookie in seconds |
| `WEB_CLIENT_LOCATION`               | No | `http://localhost:3000` | Base URL of web-client; used to hand off the OAuth2 flow after magic-link email verification when no saved request exists |
| `SPRING_MAIL_USERNAME`              | Only when `emailSender.enabled=true` | `noop` | Gmail address used to send OTP and magic-link emails |
| `SPRING_MAIL_PASSWORD`              | Only when `emailSender.enabled=true` | `noop` | Gmail App Password for the above account (not the account password; requires 2FA + App Password in Google Account settings) |

## Spring Profiles

`application.yml` is split into a shared document plus four profile documents (`---` separated, each activated via `spring.config.activate.on-profile`):

| Profile | `emailSender.enabled` | `emailSender.logToken` | Notes |
|---|---|---|---|
| `dev` | `false` | `true` | Default profile (`spring.profiles.default: dev`) — a bare `mvn spring-boot:run` runs as dev. Email is off; the OTT/magic-link token values are logged at INFO so they can be copied from the console without an inbox |
| `test` | `false` | `false` | Activated in CI (`SPRING_PROFILES_ACTIVE=test`); disables outbound email so no real Gmail credentials are needed (tokens come from the `/…/test` endpoints, not logs) |
| `qa` | `true` | `false` | Placeholder (no deploy target yet); actually sends mail |
| `prod` | `true` | `false` | Placeholder (no deploy target yet); actually sends mail |

All real configuration lives in the shared document; today only the two `emailSender.*` flags vary per profile. `EmailService` reads both via field-injected `@Value("…:false")`. When email is disabled it either logs the token value at INFO (dev, `logToken=true`) or logs a warning and skips the send (test) instead of calling `JavaMailSender`. The `:noop` defaults on `spring.mail.username`/`password` let the mail bean boot under `dev`/`test` without secrets.

`MYSQL_AUTH_SERVER_ROOT_USERNAME` and `MYSQL_AUTH_SERVER_ROOT_PASSWORD` must be provided as JVM arguments (or environment variables) at startup, for example:

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DMYSQL_AUTH_SERVER_ROOT_USERNAME=root -DMYSQL_AUTH_SERVER_ROOT_PASSWORD=secret"
```

## Running

```bash
# Full build (includes Nuxt frontend generation)
mvn package

# Run the service
mvn spring-boot:run

# Run tests only
mvn test
```

## Integration Tests

Integration tests in `src/test/java/com/roots/authserver/integration/` hit a **live running** auth-server rather than spinning up a Spring context. They require MySQL and auth-server to already be up before the test is executed.

### Prerequisites

1. Start the database:
   ```bash
   docker compose up -d auth-server-db
   ```
2. Start auth-server (from the project root or the `auth-server/` directory). The integration tests mint OTT/magic-link tokens via the `/…/test` endpoints rather than reading an inbox, so you can run under the `test` profile and skip the Gmail setup entirely:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=test -Dspring-boot.run.jvmArguments="-DMYSQL_AUTH_SERVER_ROOT_USERNAME=root -DMYSQL_AUTH_SERVER_ROOT_PASSWORD=<password>"
   ```
   To exercise real email delivery instead, run as `dev` (the default) and supply Gmail credentials:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DMYSQL_AUTH_SERVER_ROOT_USERNAME=root -DMYSQL_AUTH_SERVER_ROOT_PASSWORD=<password> -DSPRING_MAIL_USERNAME=<email> -DSPRING_MAIL_PASSWORD=<app-password>"
   ```

### Test properties

Default connection targets are declared in `src/test/resources/application.yml`:

| Property | Default | Description |
|---|---|---|
| `auth-server-location` | `http://localhost:9000` | Base URL of the running auth-server |
| `web-client-location` | `http://localhost:3000` | Base URL used as the OAuth2 redirect URI origin |
| `web-client-secret` | `secret` | Plaintext value of `WEB_CLIENT` client secret (must match `oauth2_registered_client` table) |
| `integration-test-client-secret` | `integration-test-secret` | Plaintext value of `INTEGRATION_TEST_CLIENT` client secret (must match `oauth2_registered_client` table) |

Override any of these on the command line with `-D<property>=<value>`.

### Running

```bash
# All integration tests
mvn test -Dtest="*IntegrationTest"

# Guest login integration test only
mvn test -Dtest="GuestLoginIntegrationTest"
```

### GuestLoginIntegrationTest

`GuestLoginIntegrationTest` verifies the complete guest login OAuth2 Authorization Code flow end-to-end:

1. Starts an OAuth2 authorization request (`GET /oauth2/authorize`) to seed the session with the saved request.
2. Authenticates as guest (`POST /login/guest`) and follows the redirect chain to the callback URI, extracting the authorization code.
3. Exchanges the code for tokens (`POST /oauth2/token`) and asserts that `access_token`, `token_type` (`Bearer`), `refresh_token`, and `id_token` are all present.

The `AuthServerClient` helper class manages session cookies via `java.net.CookieManager` and manually follows redirects so the code can be captured before the browser would be sent to `web-client-location/callback`.

### Self-testing via the `client_credentials` flow

auth-server is also an **OAuth2 Resource Server**, so integration tests can call a few protected, test-only endpoints on auth-server *itself* to drive flows that otherwise require reading an email (the MFA OTT and the account-creation magic link).

A dedicated machine client, **`INTEGRATION_TEST_CLIENT`** (seeded in `src/main/resources/initialize_db/create_client_table.sql`), uses the `client_credentials` grant:

| Property | Value |
|---|---|
| `clientId` | `INTEGRATION_TEST_CLIENT` |
| `clientSecret` | `{noop}integration-test-secret` |
| `grantTypes` | `client_credentials` |
| `scopes` | `INTEGRATION_TEST_CLIENT_READ` / `_WRITE` / `_UPDATE` / `_DELETE` |

A test obtains an access token (`POST /oauth2/token`, `grant_type=client_credentials`) and attaches it as a `Bearer` header to call:

| Endpoint | Guard | Purpose |
|---|---|---|
| `POST /ott/generate/test` | `INTEGRATION_TEST_CLIENT_WRITE` | Returns the MFA OTT value in the response body |
| `POST /magic-link/generate/test?email=<email>` | `INTEGRATION_TEST_CLIENT_WRITE` | Returns an account-creation magic-link token for `email` |

These endpoints are guarded with `@PreAuthorize` (method security, enabled via `@EnableMethodSecurity`). Because the default filter chain pins a custom `AuthenticationManager`, a `JwtAuthenticationProvider` is added to that manager so the bearer token can be authenticated — otherwise the bearer request fails with `ProviderNotFoundException: No AuthenticationProvider found for ...BearerTokenAuthenticationToken`.

Helper classes (`src/test/java/com/roots/authserver/integration/`):
- `IntegrationTestBase` — abstract base that all integration test classes extend. Carries the Spring test-context annotations (`@ExtendWith`/`@ContextConfiguration`/`@TestPropertySource`) and the `auth-server-location` value, and builds a **fresh** `AuthServerClient` + `OAuth2Client` before each test (`@BeforeEach`), closing them after (`@AfterEach`). See [HTTP client lifecycle](#http-client-lifecycle-per-test-fresh-clients) below for why.
- `AuthServerClient` — session-based browser interactions (authorize, create account, login, verify magic link). Holds the cookie jar, plus a separate **cookie-less** client for the bearer-authenticated `/…/test` call so it can't disturb the browser session. `AutoCloseable`; `close()` shuts down **both** its `HttpClient`s.
- `OAuth2Client` — stateless, cookie-less helper dedicated to `POST /oauth2/token`; performs the `client_credentials` exchange. `AutoCloseable`; `close()` shuts down its `HttpClient`.
- `TokenResponse` — shared record for token-endpoint responses.
- `TestConfig` — empty `@Configuration` that only anchors the test context so `@TestPropertySource`/`@Value` can resolve the connection settings (it no longer defines client beans).

### HTTP client lifecycle (per-test fresh clients)

Each test builds its own `AuthServerClient`/`OAuth2Client` and closes them afterwards (via `IntegrationTestBase`), rather than sharing one instance across the suite. This is deliberate and fixes an intermittent `java.net.ConnectException`.

**Before.** `AuthServerClient` and `OAuth2Client` were shared singleton `@Bean`s in `TestConfig`, `@Autowired` into each test class. Spring caches and reuses a single test `ApplicationContext` across all integration test classes, so every test shared one JDK `HttpClient` — and therefore one **connection pool** (the set of kept-open, reused TCP keep-alive connections). On a long run (the full suite, or CI), a pooled connection could sit idle longer than the live auth-server's Tomcat `keepAliveTimeout` (20 s). Tomcat closes its end, but the client still believes the connection is good (its own keep-alive default is 1200 s) and reuses the now-dead connection on the next request → `ClosedChannelException`, surfaced as `ConnectException`. The symptoms were distinctive: the **second** integration class to run failed (whichever one it was), each class **passed in isolation** (requests are back-to-back, so no idle gap opens), and it reproduced **locally only when the whole suite ran at once**.

**After.** The clients are no longer beans. `IntegrationTestBase` builds a fresh `AuthServerClient` + `OAuth2Client` in `@BeforeEach` and `close()`s them in `@AfterEach` (both are now `AutoCloseable`). Each test gets its own short-lived connection pool, so no connection is ever idle long enough to be reaped by the server, and nothing is shared between tests. As a bonus, every test starts with a clean cookie jar / session (the shared client had been leaking `JSESSIONID` across tests). When extending `IntegrationTestBase`, use the inherited `authServerClient` / `oAuth2Client` fields — do **not** reintroduce a shared client bean.

### CreateAccountIntegrationTest

`CreateAccountIntegrationTest` verifies the full self-service account creation + magic-link verification flow end-to-end:

1. Starts an OAuth2 authorization request to seed the session's saved request.
2. Creates an account (`POST /api/accounts`) with placeholder values and a **randomized email** (so the test is rerunnable against a persistent DB), asserting `201`.
3. Auto-logs-in (`POST /login`); the unverified email redirects to `/signup/success` (the "check your email" page).
4. Obtains an `INTEGRATION_TEST_CLIENT` access token via the `client_credentials` flow.
5. Calls `POST /magic-link/generate/test` with that token to obtain the magic-link token directly.
6. Completes verification (`POST /magic-link/login`) and follows the redirect chain to the web-client callback, asserting an authorization `code` is present.

## CI

The workflow at `.github/workflows/auth-server-ci.yml` runs on pull requests that touch `auth-server/**` (events: `opened`, `synchronize`).

### What it does

1. Starts a MySQL 8 service container and seeds it by running the scripts in `src/main/resources/initialize_db/` in order: `create_authentication_tables.sql` → `create_client_table.sql` → `create_one_time_tokens_table.sql` → `initialize_test_users.sql`.
2. Builds the JAR with `mvn package -DskipTests` (includes the Nuxt frontend build — done once).
3. Starts auth-server in the background with `java -jar`, under `SPRING_PROFILES_ACTIVE=test` (so `emailSender.enabled=false` — no real emails sent, no Gmail secrets required).
4. Polls `GET /actuator/health` until the server reports `UP` (up to 150 s).
5. Runs integration tests with `mvn surefire:test`.

### Required GitHub secrets

| Secret | Value in CI |
|---|---|
| `MYSQL_AUTH_SERVER_ROOT_USERNAME` | `root` (the MySQL service container only creates a root user) |
| `MYSQL_AUTH_SERVER_ROOT_PASSWORD` | any password |

`SPRING_MAIL_USERNAME`/`SPRING_MAIL_PASSWORD` are no longer required in CI: the `test` profile disables email, and the `:noop` defaults in `application.yml` let the `JavaMailSender` bean boot.

`MYSQL_AUTH_SERVER_DB_URL` is hardcoded in the workflow to `jdbc:mysql://localhost:3306/auth-server-db` (the GitHub Actions MySQL container exposes port 3306, not 3307).

### Notes

- `auth-server/frontend/package-lock.json` is intentionally **not committed** to git (listed in the root `.gitignore`). The `frontend-maven-plugin` generates it fresh during each build for the current platform, avoiding conflicts between Windows-generated and Linux-expected native binaries.
- The `WEB_CLIENT` registered client is seeded with secret `{noop}secret`, matching the `web-client-secret` property in `src/test/resources/application.yml`.

## CD

The workflow at `.github/workflows/auth-server-cd.yml` triggers on every push to `main` that touches `auth-server/**` (i.e. after a PR merges). Commits whose message contains `[skip ci]` are ignored — this prevents the workflow's own version-bump commit from triggering another run.

### What it does

1. Reads the current `<version>` from `pom.xml` (e.g. `0.0.1-SNAPSHOT`).
2. Strips `-SNAPSHOT` and increments the patch digit to produce the **release version** (e.g. `0.0.2`).
3. Sets `pom.xml` to the release version with `mvn versions:set`.
4. Builds and pushes the Docker image via Jib (`mvn jib:build -DskipTests`) using `eclipse-temurin:21-jre` as the base image. Two tags are pushed: the release version (e.g. `yourname/auth-server:0.0.2`) and `latest`.
5. Sets `pom.xml` to the next SNAPSHOT (e.g. `0.0.2-SNAPSHOT`) and commits it back to `main` as `github-actions[bot]` with `[skip ci]` in the commit message.

### Required GitHub secrets

| Secret | Description |
|---|---|
| `DOCKERHUB_USERNAME` | Your Docker Hub username |
| `DOCKERHUB_TOKEN` | Docker Hub access token (Account Settings → Security → Access Tokens) |

### Required one-time repo setup

1. **Workflow write permissions:** Settings → Actions → General → Workflow permissions → select **Read and write permissions**.
2. **Branch protection bypass:** Settings → Branches → main protection rule → Allow specified actors to bypass required pull requests → add **GitHub Actions**. This lets the bot commit the version bump directly to `main`.

## Account Creation

New users self-register through the Nuxt `/signup` page, which POSTs to `POST /api/accounts`. The endpoint is public (`permitAll`, CSRF disabled) and is structured controller → service → repository.

### Flow

1. The `/signup` Nuxt page (`SignupForm.vue`) collects name, email, password, and confirm-password, validating them client-side with **vee-validate** + **yup**. On success it POSTs `{ name, email, password }` as JSON to `/api/accounts`.
2. `AccountController.createAccount()` delegates to `UserCredentialService.createAccount()` (`@Transactional`).
3. `CreateAccountValidator` re-validates the request server-side — name required and ≤ 255 chars; email required and contains `@`; password required, ≥ 8 chars, with at least one uppercase letter, one lowercase letter, and one number. Any violation throws `InvalidRequestException` → **400**.
4. Duplicate emails are rejected: if `findByEmail` already returns a row, the service throws `EmailAlreadyExistsException` → **409** (the `UNIQUE(email)` constraint is the final race backstop).
5. The password is hashed with the `PasswordEncoder` bean (delegating encoder → `{bcrypt}`). A `user_guid` is generated with `UUID`, `is_mfa_enabled` is set to `true`, and `is_email_verified` to `false`.
6. `UserCredentialRepository.insert()` inserts the `user_credential` row (returning the generated id via `GeneratedKeyHolder`), then `RoleRepository.insert()` adds a default `member` role for that credential — both inside the same transaction.
7. On success the endpoint returns **201 Created** with `CreateAccountResponse(name, email)`.

### Auto-login & email verification (magic link)

Rather than make the new user re-type their credentials on the login form, the signup page **automatically starts the authorization-code flow** with the credentials just entered, which routes the brand-new (unverified) account through email verification:

1. After the `201` from `/api/accounts`, `SignupForm.vue` auto-submits a hidden native `<form method="post" action="/login">` carrying the same `email` + `password` (param names match `usernameParameter("email")` / default `password`; CSRF is disabled). The browser navigates, handing off to Spring Security's form-login pipeline.
2. `MfaAwareDaoAuthenticationProvider` validates the password, sees `is_email_verified = false`, and returns a `CreateAccountPendingAuthenticationToken` (not yet authenticated).
3. `MfaRedirectAuthenticationSuccessHandler` builds an absolute magic-link URL — `…/magic-link/login?magicLinkToken=<otp>` via `ServletUriComponentsBuilder.fromCurrentContextPath()` on the request thread — emails it with `EmailService.sendMagicLinkEmail`, and redirects the browser to `/signup/success` ("check your email to continue").
4. The user clicks the link → `GET /magic-link/login?magicLinkToken=<otp>`. `SpaController.forwardMagicLinkSent` binds the token with `@RequestParam` and **saves it as a session attribute** (`magicLinkToken`) before forwarding to the Nuxt page.

   **Why the session?** The `magic-link/login` page is a statically-generated SSR Nuxt route. When it hydrates in the browser, Vue Router resyncs the address bar to the route's canonical path and **strips the query string** — by the time any component hook runs, `window.location.search` (and `useRoute().query`) is empty, so the page cannot read `magicLinkToken` from the URL. (A Spring `forward:` is server-internal and never changes the browser URL, so this stripping is purely client-side.) The server, however, sees the query reliably on this GET. So we capture the token server-side and stash it in the HTTP session; the token never has to round-trip through client JavaScript.

5. The page renders a **"Continue with login"** button — a native `<form method="post" action="/magic-link/login">` with no token field and **no auto-submit**. The user clicks it → `POST /magic-link/login` carrying no token. `SpaController.verifyMagicLink` reads `magicLinkToken` back **from the session**, consumes it via `JdbcOneTimeTokenService` (a delete-and-return against the `one_time_tokens` table, then checks the returned `username` matches the pending account's email), removes the session attribute, calls `UserCredentialService.verifyEmail` (sets `is_email_verified = true`), builds a fully authenticated `MfaAuthenticationToken`, saves it to the session, and redirects to the **saved OAuth2 request** — completing the authorization-code grant back to web-client. (If the session has no `magicLinkToken`, or it fails to consume, it redirects to `/magic-link/login?error=invalidToken`.)
6. If there is **no** saved request (the user reached `/signup` directly, not via an OAuth2 flow), `verifyMagicLink` instead redirects to the web-client base URL (`web-client.location`, default `http://localhost:3000`). web-client then initiates `/oauth2/authorize` with its own `state`; since the session is already authenticated, a code is issued and its callback succeeds. (The auth-server cannot initiate the flow itself because web-client validates `state` against its own `sessionStorage`.)

> **Limitations.** The magic link must be opened in the **same browser** that signed up — the pending token, the saved request, **and the captured `magicLinkToken`** all live in that one HTTP session. Requiring a button click (instead of auto-submitting on mount) means an email client/scanner that merely prefetches the link cannot trigger the consuming POST.

### Key classes

| Class | Package | Role |
|---|---|---|
| `AccountController` | `controller/` | `POST /api/accounts`; `@ResponseStatus(CREATED)`; delegates to `UserCredentialService` |
| `CreateAccountRequest` | `dto/request/` | Record `(name, email, password)` — request body |
| `CreateAccountResponse` | `dto/response/` | Record `(name, email)` — response body |
| `CreateAccountValidator` | `service/` | Server-side field validation; throws `InvalidRequestException` |
| `UserCredentialService` | `service/` | `createAccount(request)` (`@Transactional`); `verifyEmail(email)` sets `is_email_verified = true` |
| `UserCredentialRepository` | `repository/` | `insert(UserCredential)` returns the generated id via `GeneratedKeyHolder`; `verifyEmail(email)` |
| `RoleRepository` | `repository/` | `insert(credentialId, roleName)` — adds a role row with a generated `role_guid` |
| `MfaAwareDaoAuthenticationProvider` | `component/` | Routes an unverified account to `CreateAccountPendingAuthenticationToken` after validating the password |
| `CreateAccountPendingAuthenticationToken` | `principal/` | First factor passed but email unverified; `isAuthenticated() = false` |
| `MfaRedirectAuthenticationSuccessHandler` | `component/` | For the pending token, emails the magic link and redirects to `/signup/success` |
| `SpaController` | `controller/` | `forwardMagicLinkSent` (`GET /magic-link/login`) captures `magicLinkToken` from the query into the session (the hydrated SPA can't read it); `verifyMagicLink` (`POST /magic-link/login`) reads it back from the session, consumes it, verifies email, upgrades the session, redirects to the saved request or web-client |
| `EmailService` | `service/` | `sendMagicLinkEmail(to, magicLink)` — sends the verification link via Gmail SMTP |
| `InvalidRequestException` | `exception/` | Generic bad-request signal → **400** |
| `EmailAlreadyExistsException` | `exception/` | Duplicate email → **409** |
| `GlobalExceptionHandler` | `exception/` | `@RestControllerAdvice` mapping the two exceptions to 400 / 409 with a `{"error": "..."}` body |

| Class | Package | Role |
|---|---|---|
| `AccountController` | `controller/` | `POST /api/accounts`; `@ResponseStatus(CREATED)`; delegates to `UserCredentialService` |
| `CreateAccountRequest` | `dto/request/` | Record `(name, email, password)` — request body |
| `CreateAccountResponse` | `dto/response/` | Record `(name, email)` — response body |
| `CreateAccountValidator` | `service/` | Server-side field validation; throws `InvalidRequestException` |
| `UserCredentialService` | `service/` | `createAccount(request)` — orchestrates validation, duplicate check, hashing, and both inserts (`@Transactional`) |
| `UserCredentialRepository` | `repository/` | `insert(UserCredential)` returns the generated id via `GeneratedKeyHolder` |
| `RoleRepository` | `repository/` | `insert(credentialId, roleName)` — adds a role row with a generated `role_guid` |
| `InvalidRequestException` | `exception/` | Generic bad-request signal → **400** |
| `EmailAlreadyExistsException` | `exception/` | Duplicate email → **409** |
| `GlobalExceptionHandler` | `exception/` | `@RestControllerAdvice` mapping the two exceptions to 400 / 409 with a `{"error": "..."}` body |

## Multi-Factor Authentication (MFA)

MFA is **optional per user**, controlled by the `is_mfa_enabled` column in `user_credential` (default `true`). Users whose `is_mfa_enabled` is `false` are fully authenticated immediately after the first factor and skip the OTT step entirely.

### Flow (MFA enabled)

1. User submits credentials on `/login` (or browser sends a remember-me cookie automatically).
2. Spring Security validates the first factor. Because `is_mfa_enabled = true`, the session holds a `MfaPendingAuthenticationToken` — the user is **not yet authenticated** and has no granted authorities.
3. The `MfaRedirectAuthenticationSuccessHandler` detects the pending token and redirects to `/ott/login`.
4. The `/ott/login` Nuxt page loads and immediately calls `POST /ott/generate`. This generates a one-time token and hands it to `EmailService`, which **emails it** via Gmail SMTP (qa/prod), **logs the value at INFO** to the server console (dev — copy it from there, no inbox needed), or skips it (test).
5. The user copies the token from the console, enters it in the OTT form, and submits `POST /ott/login`. The form also includes a "Remember this browser?" checkbox.
6. `SpaController` verifies the token. If "Remember this browser?" was checked, it sets `is_mfa_enabled = false` for the user in the database. The session is upgraded to a fully authenticated `MfaAuthenticationToken` and the user is redirected back to the original OAuth2 authorization request.

### Flow (MFA disabled)

1. User submits credentials or browser sends a remember-me cookie.
2. The authentication provider checks `is_mfa_enabled`. Because it is `false`, a fully authenticated `MfaAuthenticationToken` is returned directly — no `MfaPendingAuthenticationToken` is created.
3. `MfaRedirectAuthenticationSuccessHandler` sees a non-pending token and falls through to the standard saved-request redirect (the OAuth2 flow).

### Key classes

| Class | Package | Role |
|---|---|---|
| `MfaPendingAuthenticationToken` | `principal/` | Represents a session that has passed the first factor but not yet MFA; `isAuthenticated() = false` |
| `MfaAuthenticationToken` | `principal/` | Represents a fully authenticated session |
| `MfaAwareDaoAuthenticationProvider` | `component/` | Validates password; checks `is_mfa_enabled` and returns either `MfaPendingAuthenticationToken` or `MfaAuthenticationToken` |
| `MfaAwareRememberMeAuthenticationProvider` | `component/` | Validates remember-me cookie; same conditional MFA logic |
| `MfaRedirectAuthenticationSuccessHandler` | `component/` | Redirects to `/ott/login` when the result is a pending token; falls through to saved-request redirect otherwise |
| `MfaController` | `controller/` | `POST /ott/generate` — generates the OTT and calls `EmailService.sendOTTEmail` (which emails it, logs it at INFO in dev, or skips it in test) |
| `SpaController` | `controller/` | `GET /ott/login` (forward to Nuxt page) + `POST /ott/login` (verify OTT, optionally disable MFA, and upgrade session) |
| `UserCredential` | `model/` | Record representing a row from `user_credential` |
| `UserCredentialRepository` | `repository/` | JdbcTemplate-based repo; `findByEmail` and `setMfaEnabled` |
| `UserCredentialService` | `service/` | `isMfaEnabled(email)` and `disableMfa(email)`; injected into both authentication providers and `SpaController` |
| `EmailService` | `service/` | `sendOTTEmail(to, ott)` — sends the OTT to the user's email via Gmail SMTP using `JavaMailSender` |

### OTT storage

Two separate one-time-secret stores:

- **MFA one-time PIN** — the custom `InMemoryOneTimePinService` (6-digit numeric code). In-memory only; lost on server restart — **dev/test only**.
- **Account-creation magic-link token** — Spring Security's `JdbcOneTimeTokenService`, backed by the `one_time_tokens` table (`token_value` PK, `username`, `expires_at`). Tokens **survive restart**. The table is standalone (no FK to `user_credential`); `username` holds the user's email, which `verifyMagicLink` checks against the pending account after consuming the token by its value. Default TTL is 5 minutes. Expired-but-unclicked rows are not cleaned up (acceptable at dev/test scale).

## Guest Login

The login page includes a "Continue as Guest" button that allows access without credentials. Clicking it submits an empty form to `POST /login/guest`.

### Flow

1. The login form (`LoginForm.vue`) contains two separate HTML forms — `#login-form` (email/password) and `#guest-form` (no fields). The "Continue as Guest" button submits `#guest-form`.
2. `SpaController.loginAsGuest()` receives the request and calls `authenticationManager.authenticate(new GuestAuthenticationToken())`.
3. `GuestAuthenticationProvider` handles the token. It creates a synthetic `UserDetails` with username `"guest"` and authority `"GUEST"` and returns a fully authenticated `MfaAuthenticationToken` — no password validation or MFA check is performed.
4. The resulting `SecurityContext` is saved to the HTTP session and the user is redirected to the saved OAuth2 authorization request. If no saved request is found, the user is redirected to `/login?error=oauthRedirectFailed`.

### Key classes

| Class | Package | Role |
|---|---|---|
| `GuestAuthenticationToken` | `principal/` | Marker token with no credentials or authorities; used only to route the request to `GuestAuthenticationProvider` |
| `GuestAuthenticationProvider` | `component/` | Supports `GuestAuthenticationToken`; builds a `"guest"` / `"GUEST"` principal and returns a fully authenticated `MfaAuthenticationToken` |

### simple-resource-server — guest endpoint

The `/api/role/guest` endpoint in `simple-resource-server` requires a valid JWT with the `WEB_CLIENT_READ` scope but no role claim, so a guest-authenticated user can access it as long as an access token has been issued for them.

## Remember Me

The login form includes an optional "Remember Me?" checkbox. When checked, Spring Security sets a `remember-me` cookie (SHA-256 token, valid for `REMEMBER_ME_TOKEN_VALIDITY_SECONDS`) that automatically re-authenticates the user on their next visit. When the box is unchecked, no cookie is issued and the session ends when the browser closes.

If a user has MFA enabled, remember-me re-authentication still goes through the OTT flow — `MfaAwareRememberMeAuthenticationProvider` issues a `MfaPendingAuthenticationToken` and the user is redirected to `/ott/login`. If the user has previously checked "Remember this browser?", `is_mfa_enabled` will be `false` and the OTT step is skipped entirely.

`REMEMBER_ME_KEY` should be a stable secret in production — changing it invalidates all existing remember-me cookies.

## Logout

Auth-server implements **OIDC RP-Initiated Logout** (`GET /connect/logout`). The client initiates logout by redirecting the user to this endpoint with an `id_token_hint` and a `post_logout_redirect_uri`. The server invalidates the session and redirects back to the specified URI.

### Flow

1. The user clicks "logout" in `web-client`. The client clears its sessionStorage tokens and redirects to `GET /connect/logout?post_logout_redirect_uri=http://localhost:3000/logout&id_token_hint=<id_token>`.
2. Auth-server validates the `id_token_hint` against the active session and confirms the `post_logout_redirect_uri` matches a registered value for the client.
3. `RememberMeOidcLogoutAuthenticationSuccessHandler` clears the `remember-me` cookie (setting `Max-Age=0`), then delegates to the standard `OidcLogoutAuthenticationSuccessHandler`, which invalidates the server-side session and redirects to `post_logout_redirect_uri`.
4. The user lands on `web-client`'s `/logout` page, which clears any remaining sessionStorage tokens and offers a button to re-authorize.

### Key class

| Class | Package | Role |
|---|---|---|
| `RememberMeOidcLogoutAuthenticationSuccessHandler` | `component/` | Wraps `OidcLogoutAuthenticationSuccessHandler`; clears the `remember-me` cookie before delegating to the standard OIDC logout response |

### Registered `post_logout_redirect_uri`

The `WEB_CLIENT` seed in `create_client_table.sql` sets `post_logout_redirect_uris` to `http://localhost:3000/logout`. Logout requests specifying any other URI are rejected.

### `MfaAuthenticationToken` and `FactorGrantedAuthority`

Spring Security 7's OIDC logout validation requires that authorities on the session's `Authentication` are `FactorGrantedAuthority` instances (carrying an `issuedAt` timestamp). `MfaAuthenticationToken` wraps each authority using `FactorGrantedAuthority.withAuthority(...).issuedAt(Instant.now()).build()` to satisfy this requirement.

## Database

Connects to a MySQL 8 instance on port **3307** by default. The schema is defined in `src/main/resources/initialize_db/`. Hibernate is set to `validate` mode — it checks that the schema matches the JPA entities on startup but makes no changes to the database.

### Tables

| Table | Columns |
|---|---|
| `user_credential` | `id`, `user_guid`, `email`, `name`, `password`, `is_mfa_enabled` (default `true`), `is_email_verified` (default `false`), `creation_date`, `update_date` |
| `role` | `id`, `role_guid`, `credential_id` (FK → `user_credential`), `role_name`, `creation_date`, `update_date` |
| `oauth2_registered_client` | `id`, `client_id`, `client_id_issued_at`, `client_secret`, `client_secret_expires_at`, `client_name`, `client_authentication_methods`, `authorization_grant_types`, `redirect_uris`, `post_logout_redirect_uris`, `scopes`, `client_settings`, `token_settings` |
