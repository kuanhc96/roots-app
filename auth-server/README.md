# auth-server

A fullstack Spring Boot + Nuxt/Vue application that handles authentication for the roots-app platform. It embeds the Nuxt frontend as a static SPA served by Spring Boot, and manages user credentials and roles in a MySQL database.

## Environment Variables

| Variable                          | Required | Default | Description                     |
|-----------------------------------|---|---|---------------------------------|
| `MYSQL_AUTH_SERVER_ROOT_USERNAME` | Yes | — | MySQL username                  |
| `MYSQL_AUTH_SERVER_ROOT_PASSWORD` | Yes | — | MySQL password                  |
| `WEB_CLIENT_SECRET`               | Yes | — | OAuth2 client secret for the registered `WEB_CLIENT`; must match `NUXT_PUBLIC_WEB_CLIENT_SECRET` in web-client |
| `MYSQL_AUTH_SERVER_DB_URL`        | No | `jdbc:mysql://localhost:3307/auth-server-db` | JDBC connection URL             |
| `SERVER_PORT`                     | No | `9000` | HTTP port the server listens on |
| `WEB_CLIENT_REDIRECT_URI`         | No | `http://localhost:3000/callback` | OAuth2 redirect URI registered for `WEB_CLIENT`; must match the callback page URL in web-client |
| `REMEMBER_ME_KEY`                 | No | `dev-remember-me-key-change-in-prod` | Secret key used to sign remember-me cookies; change in production |
| `REMEMBER_ME_TOKEN_VALIDITY_SECONDS` | No | `1209600` (14 days) | Lifetime of the remember-me cookie in seconds |

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

Every login — whether via username/password or a remember-me cookie — requires a one-time token (OTT) second factor before a fully authenticated session is established.

### Flow

1. User submits credentials on `/login` (or browser sends a remember-me cookie automatically).
2. Spring Security validates the first factor. On success, the session holds a `MfaPendingAuthenticationToken` — the user is **not yet authenticated** and has no granted authorities.
3. The `MfaRedirectAuthenticationSuccessHandler` detects the pending token and redirects to `/ott/login`.
4. The `/ott/login` Nuxt page loads and immediately calls `POST /ott/generate`. This generates a one-time token and **prints it to the server console** (dev mode — no email or SMS delivery yet).
5. The user copies the token from the console, enters it in the OTT form, and submits `POST /ott/login`.
6. `SpaController` verifies the token, upgrades the session to a fully authenticated `MfaAuthenticationToken`, and redirects back to the original OAuth2 authorization request.

### Key classes

| Class | Package | Role |
|---|---|---|
| `MfaPendingAuthenticationToken` | `principal/` | Represents a session that has passed the first factor but not yet MFA; `isAuthenticated() = false` |
| `MfaAuthenticationToken` | `principal/` | Represents a fully authenticated session after OTT verification |
| `MfaAwareDaoAuthenticationProvider` | `component/` | Replaces `DaoAuthenticationProvider`; returns `MfaPendingAuthenticationToken` on password success |
| `MfaAwareRememberMeAuthenticationProvider` | `component/` | Extends `RememberMeAuthenticationProvider`; returns `MfaPendingAuthenticationToken` on cookie success |
| `MfaRedirectAuthenticationSuccessHandler` | `component/` | Redirects to `/ott/login` when the result is a pending token; wired into both form-login and remember-me filters |
| `MfaController` | `controller/` | `POST /ott/generate` — generates the OTT and logs it to stdout |
| `SpaController` | `controller/` | `GET /ott/login` (forward to Nuxt page) + `POST /ott/login` (verify OTT and upgrade session) |

### OTT storage

`InMemoryOneTimeTokenService` (Spring Security built-in) is used. Tokens are lost on server restart — **dev/test only**. A persistent token store should be wired in for production.

## Remember Me

The login form includes an optional "Remember Me?" checkbox. When checked, Spring Security sets a `remember-me` cookie (SHA-256 token, valid for `REMEMBER_ME_TOKEN_VALIDITY_SECONDS`) that automatically re-authenticates the user on their next visit. When the box is unchecked, no cookie is issued and the session ends when the browser closes.

Remember-me re-authentication also goes through the full MFA flow — `MfaAwareRememberMeAuthenticationProvider` issues a `MfaPendingAuthenticationToken` and the user is redirected to `/ott/login` to complete the second factor.

`REMEMBER_ME_KEY` should be a stable secret in production — changing it invalidates all existing remember-me cookies.

## Database

Connects to a MySQL 8 instance on port **3307** by default. The schema is defined in `src/main/resources/initialize_db/`. Hibernate is set to `validate` mode — it checks that the schema matches the JPA entities on startup but makes no changes to the database.

### Tables

| Table | Columns |
|---|---|
| `user_credential` | `id`, `user_guid`, `email`, `password`, `is_mfa_enabled` (default `true`), `is_email_verified` (default `false`), `creation_date`, `update_date` |
| `role` | `id`, `role_guid`, `credential_id` (FK → `user_credential`), `role_name`, `creation_date`, `update_date` |
