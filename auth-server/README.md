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
| `SPRING_MAIL_USERNAME`              | Yes | — | Gmail address used to send OTP emails |
| `SPRING_MAIL_PASSWORD`              | Yes | — | Gmail App Password for the above account (not the account password; requires 2FA + App Password in Google Account settings) |

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

## Multi-Factor Authentication (MFA)

MFA is **optional per user**, controlled by the `is_mfa_enabled` column in `user_credential` (default `true`). Users whose `is_mfa_enabled` is `false` are fully authenticated immediately after the first factor and skip the OTT step entirely.

### Flow (MFA enabled)

1. User submits credentials on `/login` (or browser sends a remember-me cookie automatically).
2. Spring Security validates the first factor. Because `is_mfa_enabled = true`, the session holds a `MfaPendingAuthenticationToken` — the user is **not yet authenticated** and has no granted authorities.
3. The `MfaRedirectAuthenticationSuccessHandler` detects the pending token and redirects to `/ott/login`.
4. The `/ott/login` Nuxt page loads and immediately calls `POST /ott/generate`. This generates a one-time token, **prints it to the server console** (for debugging), and **emails it to the user** via `EmailService` (Gmail SMTP).
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
| `MfaController` | `controller/` | `POST /ott/generate` — generates the OTT, logs it to stdout, and calls `EmailService.sendOTTEmail` |
| `SpaController` | `controller/` | `GET /ott/login` (forward to Nuxt page) + `POST /ott/login` (verify OTT, optionally disable MFA, and upgrade session) |
| `UserCredential` | `model/` | Record representing a row from `user_credential` |
| `UserCredentialRepository` | `repository/` | JdbcTemplate-based repo; `findByEmail` and `setMfaEnabled` |
| `UserCredentialService` | `service/` | `isMfaEnabled(email)` and `disableMfa(email)`; injected into both authentication providers and `SpaController` |
| `EmailService` | `service/` | `sendOTTEmail(to, ott)` — sends the OTT to the user's email via Gmail SMTP using `JavaMailSender` |

### OTT storage

`InMemoryOneTimeTokenService` (Spring Security built-in) is used. Tokens are lost on server restart — **dev/test only**. A persistent token store should be wired in for production.

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

## Database

Connects to a MySQL 8 instance on port **3307** by default. The schema is defined in `src/main/resources/initialize_db/`. Hibernate is set to `validate` mode — it checks that the schema matches the JPA entities on startup but makes no changes to the database.

### Tables

| Table | Columns |
|---|---|
| `user_credential` | `id`, `user_guid`, `email`, `password`, `is_mfa_enabled` (default `true`), `is_email_verified` (default `false`), `creation_date`, `update_date` |
| `role` | `id`, `role_guid`, `credential_id` (FK → `user_credential`), `role_name`, `creation_date`, `update_date` |
| `oauth2_registered_client` | `id`, `client_id`, `client_id_issued_at`, `client_secret`, `client_secret_expires_at`, `client_name`, `client_authentication_methods`, `authorization_grant_types`, `redirect_uris`, `post_logout_redirect_uris`, `scopes`, `client_settings`, `token_settings` |
