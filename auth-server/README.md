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

## Integration Tests

Integration tests in `src/test/java/com/roots/authserver/integration/` hit a **live running** auth-server rather than spinning up a Spring context. They require MySQL and auth-server to already be up before the test is executed.

### Prerequisites

1. Start the database:
   ```bash
   docker compose up -d auth-server-db
   ```
2. Start auth-server (from the project root or the `auth-server/` directory):
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
| `user_credential` | `id`, `user_guid`, `email`, `password`, `is_mfa_enabled` (default `true`), `is_email_verified` (default `false`), `creation_date`, `update_date` |
| `role` | `id`, `role_guid`, `credential_id` (FK → `user_credential`), `role_name`, `creation_date`, `update_date` |
| `oauth2_registered_client` | `id`, `client_id`, `client_id_issued_at`, `client_secret`, `client_secret_expires_at`, `client_name`, `client_authentication_methods`, `authorization_grant_types`, `redirect_uris`, `post_logout_redirect_uris`, `scopes`, `client_settings`, `token_settings` |
