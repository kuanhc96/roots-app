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
| `bff-server` | Spring Boot (Maven) | 8083 | Backend-for-frontend; will manage web-client's tokens server-side in Redis-backed sessions (currently scaffolding only) |
| `simple-resource-server` | Spring Boot (Maven) | 8081 | Example protected resource with role endpoints |
| `account-management` | Spring Boot (Maven) | 8082 | Account CRUD resource server (integration-test-only endpoints so far) |
| `web-client` | Nuxt 4 / Vue 3 | 3000 | Standalone frontend |

**Startup order:** config-server → eureka-server → gateway-server → auth-server → bff-server → simple-resource-server → account-management → web-client.

### auth-server is special

It bundles a Nuxt frontend via Maven. The Maven build:
1. Installs Node.js and runs `npm install` + `npm run generate` in `frontend/`
2. Copies `frontend/.output/public` into the build's `target/classes/static` (`maven-resources-plugin`, `process-resources` phase — nothing is written into `src/`)
3. `SpaFallbackConfig` (in `config/`) registers a `/**` resource handler with a `PathResourceResolver` fallback: real static assets (`/_nuxt/*`, favicon) are served as files, and **any other GET falls back to `index.html`**, where the Nuxt client router owns the path (`/login`, `/forgot-password`, …). The only per-page controller forwards left are `AuthFlowController.forwardSpaShell` for `/ott/login`, `/magic-link/login`, `/reset-password`, and `/signup` — those paths also carry a `@PostMapping`, and Spring MVC answers a path-match/method-mismatch with 405 instead of falling through to the resource handler.

The frontend is built in **SPA mode** (`ssr: false` in `nuxt.config.ts`): `nuxt generate` emits one unrendered shell (`index.html`; the per-route HTML files it also writes are identical shells), there is **no hydration**, and the router boots from `window.location` — so query params like `?e=…` or `?magicLinkToken=…` are readable via `useRoute().query` on every full page load. (Previously the site was prerendered with `ssr: true`; each route hydrated against a build-time payload that dropped the runtime query string, which forced per-route forwards in the old `SpaController` and a session-stashed magic-link token.)

The `auth-server-db` MySQL instance runs on port **3307** (not the default 3306) and is defined in `docker-compose.yml`. DB schema is in `auth-server/src/main/resources/initialize_db/`.

**Required env vars at startup:** `MYSQL_AUTH_SERVER_ROOT_USERNAME` and `MYSQL_AUTH_SERVER_ROOT_PASSWORD` (no defaults). `MYSQL_AUTH_SERVER_DB_URL` defaults to `jdbc:mysql://localhost:3307/auth-server-db`. `SERVER_PORT` defaults to `9000`. `REMEMBER_ME_KEY` defaults to `dev-remember-me-key-change-in-prod` (change in production). `REMEMBER_ME_TOKEN_VALIDITY_SECONDS` defaults to `1209600` (14 days). `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD` (no defaults) are **required in every profile**: the `JavaMailSender` is auto-configured in all profiles, so these must be supplied as env/JVM vars (CI supplies them as GitHub secrets) or startup fails fast; use a Gmail App Password, not the account password. They must be valid wherever `/actuator/health` is polled, because the Actuator mail health indicator opens a real SMTP connection. Whether mail is actually *sent* is gated separately by `emailSender.enabled` (off in `dev`/`test`, on in `qa`/`prod`). `WEB_CLIENT_LOCATION` (property `web-client.location`) defaults to `http://localhost:3000` and is used to hand off to web-client after magic-link email verification when no saved OAuth2 request exists. `GOOGLE_CLIENT_ID` (property `google.client-id`, safe public default) is the expected `aud` when verifying Google id_tokens — a normal server runtime var.

**`NUXT_PUBLIC_GOOGLE_CLIENT_SECRET` is a frontend *build-time* env var, not a JVM/server var.** The embedded frontend is statically generated (`ssr: false` + `nuxt generate`), so `runtimeConfig.public.*` values — including `NUXT_PUBLIC_*` overrides — are **baked into the JS bundle when `npm run generate` runs**; there is no Nitro server at runtime to inject them. The Google client secret (used by the `/callback` page's browser-side code-for-token exchange) must therefore be present in the environment of the **Maven build** (`frontend-maven-plugin` spawns npm, which inherits the shell env — a `-D` flag sets a Java system property that npm never sees) or in `auth-server/frontend/.env` (gitignored via `**/.env`; the Nuxt CLI loads it for both `npm run dev` and `npm run generate`). Changing it requires a **rebuild** — a JAR built without it fails Google login until rebuilt, and restarting with new env vars cannot fix it. The secret ships readable in the served bundle: an accepted tradeoff of the browser-side exchange until it moves server-side (planned), when it becomes a normal server env var. Both auth-server workflows export it from the `GOOGLE_CLIENT_SECRET` GitHub secret on their build steps: `auth-server-ci.yml` on `mvn package` (proves the project builds with the secret wired in — no test exercises Google login) and `auth-server-cd.yml` on the image build (the pushed image's bundle must carry it).

**Spring profiles (`dev`, `test`, `qa`, `prod`).** `application.yml` is split into a shared document plus four profile documents (`---` separated, each activated via `spring.config.activate.on-profile`). The shared document holds all real config and sets `spring.profiles.default: dev`, so a bare run with no profile active behaves as `dev`. Two per-profile flags drive email today: `emailSender.enabled` (`true` for `qa`/`prod`, `false` for `dev`/`test`) and `emailSender.logToken` (`true` only for `dev`). `EmailService` reads both via field-injected `@Value("…:false")` (the `:false` fallbacks fail closed). When email is disabled it either logs the OTT/magic-link **token value at INFO** (dev, `logToken=true` — a console debugging aid, no inbox needed) or logs a warning and skips silently (test). The `JavaMailSender` itself is auto-configured from `spring.mail.*` in **every** profile (no auto-config exclusion), so `EmailService` injects it as a required `private final MailSender` (the concrete bean is a `JavaMailSenderImpl`); `SPRING_MAIL_USERNAME`/`SPRING_MAIL_PASSWORD` have no defaults and must be provided everywhere. `qa`/`prod` are placeholders (no deploy target yet) that actually send mail. CI activates `test` (`SPRING_PROFILES_ACTIVE=test`) so no real emails are sent, but it still supplies real `SPRING_MAIL_*` secrets — the mail bean is built and the Actuator mail health indicator opens an SMTP connection on each `/actuator/health` poll, so the credentials must be valid for the healthcheck to pass. (Because `EmailService` is the chokepoint, the magic-link value is logged there; the old unconditional `System.out.println` in `MfaRedirectAuthenticationSuccessHandler` was removed so the link no longer leaks to stdout in `qa`/`prod`.)

### Spring Security / OAuth2 Authorization Server

Auth-server runs as an **OAuth2 Authorization Server** (Spring Authorization Server 2.x, Spring Security 7.x). All security beans live in `config/SecurityConfig.java`.

- Form login uses `email` as the username field (not `username`)
- CORS and CSRF are disabled globally
- The SPA and Spring Security coexist on `/login`: GET `/login` falls through to the static-resource fallback (`SpaFallbackConfig` serves the SPA shell); POST `/login` is intercepted by Spring Security's filter before MVC for credential processing. A failed login redirects to `/login?e=invalid_login` (`failureUrl`) — one generic code for unknown email and wrong password alike, so responses never reveal whether an email has an account; the frontend maps codes to display text in `frontend/utils/errorMessages.ts` (read via the `useServerErrorMessage` composable, which also scrubs the code from the URL after mount); on the Java side every emitted code comes from the `ErrorCode` enum (`enums/`), the exact mirror of that map (`NO_MFA_PENDING` is reserved — mapped but not currently emitted)
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
| `PasswordChangePendingAuthenticationToken` | Issued after a temp-password login when `is_password_change_required=true`; `isAuthenticated()=false`, no authorities; held in the session until the new password is set at `/reset-password` (see Forgot-Password / Reset Flow) |
| `CreateAccountPendingAuthenticationToken` | Issued after first-factor success when `is_email_verified=false`; held until the magic link is verified (see Account Creation Flow) |

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
| `GET /ott/login` | `AuthFlowController.forwardSpaShell` | Forwards to the SPA shell; the client router renders the OTT login page. Explicit because the path also has a `@PostMapping`: a URL that matches a mapping's path but not its method gets a 405 from Spring MVC instead of falling through to the static fallback (same for `/magic-link/login`, `/reset-password`, and `/signup`) |
| `POST /ott/login` | `AuthFlowController` | Verifies the submitted OTT; if `rememberBrowser=true` is posted, disables MFA for the user; upgrades the session to `MfaAuthenticationToken` and redirects to the original OAuth2 request |

**Guest login endpoint:**

| Endpoint | Handler | Purpose |
|---|---|---|
| `POST /login/guest` | `AuthFlowController` | Authenticates the user as guest (no credentials required); creates a `GuestAuthenticationToken`, delegates to `GuestAuthenticationProvider` via `AuthenticationManager`, saves the resulting `MfaAuthenticationToken` to the session, and redirects to the saved OAuth2 request |

**OTT storage:** the MFA one-time PIN uses the custom in-memory `InMemoryOneTimePinService` (lost on restart — dev/test only). The account-creation magic-link token uses Spring Security's `JdbcOneTimeTokenService`, persisted in the `one_time_tokens` table (survives restart).

**Supporting classes (`model/`, `repository/`, `service/`):**

| Class | Role |
|---|---|
| `UserCredential` | Record mapping a `user_credential` row |
| `UserCredentialRepository` | JdbcTemplate-based repo; `findByEmail` and `setMfaEnabled` |
| `UserCredentialService` | `isMfaEnabled(email)` and `disableMfa(email)`; injected into both auth providers and `AuthFlowController` |
| `EmailService` | `sendOTTEmail(to, ott)` — sends the OTT to the user's email via Gmail SMTP using a required `MailSender` (auto-configured `JavaMailSenderImpl`, built in every profile); injected into `MfaRedirectAuthenticationSuccessHandler` |

**Full MFA flow (MFA enabled):**
1. User submits credentials or browser sends remember-me cookie.
2. The appropriate MFA-aware provider validates the first factor, checks `is_mfa_enabled = true`, and stores a `MfaPendingAuthenticationToken` in the session.
3. `MfaRedirectAuthenticationSuccessHandler` detects the pending token, generates the OTT via `InMemoryOneTimePinService`, and hands it to `EmailService` — emailed to the user (qa/prod), logged at INFO to the server console (dev), or skipped (test) — then redirects to `/ott/login`. (There is no public `/ott/generate` endpoint; generation is entirely server-side within the login request.)
4. The Nuxt `/ott/login` page mounts the OTT form — purely presentational, no API call.
5. User enters the OTT and optionally checks "Remember this browser?", then submits `POST /ott/login`.
6. `AuthFlowController.verifyOtt()` consumes the token. If `rememberBrowser=true`, it calls `UserCredentialService.disableMfa()` to set `is_mfa_enabled = false`. The session is upgraded to `MfaAuthenticationToken` and redirected to the saved OAuth2 authorization request.

**MFA disabled flow:**
1. Provider validates first factor, checks `is_mfa_enabled = false`, and returns `MfaAuthenticationToken` directly.
2. `MfaRedirectAuthenticationSuccessHandler` sees a non-pending token and falls through to the saved-request redirect — no OTT step.

#### Account Creation Flow

New users self-register via the Nuxt `/signup` page → a **native form `POST /signup`** (public; `permitAll`, CSRF disabled) — a browser navigation, not an AJAX call, so the server owns every redirect. `GET /signup` is served by `AuthFlowController.forwardSpaShell` (the path now carries a `@PostMapping`, so it can't rely on the static fallback):

1. `SignupForm.vue` validates name/email/password/confirm client-side (vee-validate + yup). The Vuetify fields bind to a native `<form id="signup-form" method=post action=/signup>` via the HTML `form` attribute (same pattern as `LoginForm.vue`); vee-validate's `handleSubmit` intercepts the submit event and, when valid, resumes the native submission (`evt.target.submit()`) posting `name`/`email`/`password` form-encoded (confirm-password is client-side only and not bound to the form).
2. `AuthFlowController.signup()` (`POST /signup`) calls `UserCredentialService.createAccount()` (`@Transactional`).
3. `Validator.validateCreateAccountRequest` re-validates server-side (name ≤ 255; email contains `@`; password ≥ 8 with at least one uppercase, lowercase, and digit) — `InvalidRequestException` is caught and answered with `302 /signup?e=invalid_request&name=…&email=…` (strictly percent-encoded via `UriUtils.encode`; the page prefills name/email so only the password is re-typed). (`Validator` is the shared validation component in `validator/`; its `validatePassword` method is the single source of truth for the password policy, reused by the forgot-password reset flow.)
4. Duplicate email → `EmailAlreadyExistsException`, caught and answered with `302 /signup?e=email_taken&name=…&email=…`; `UNIQUE(email)` is the race backstop.
5. Password hashed via the `PasswordEncoder` bean (`{bcrypt}`); `user_guid` from `UUID`; `is_mfa_enabled=true`, `is_email_verified=false`.
6. `UserCredentialRepository.insert()` inserts the credential (id via `GeneratedKeyHolder`); `RoleRepository.insert()` adds a default `member` role.

**Pending session + email verification (magic link).** The account was created in this very request, so the first factor is proven without a login round trip — the controller establishes the email-verification pending session directly:

7. `AuthFlowController.signup()` loads the new `UserDetails` via the `UserDetailsService`, stores a `CreateAccountPendingAuthenticationToken` in the session (`HttpSessionSecurityContextRepository.saveContext`, exactly the state a form login with an unverified email would produce), calls `MagicLinkService.issueAndEmail(email)`, and returns `302 /signup/success`. (`MagicLinkService` generates the token via `JdbcOneTimeTokenService`, builds the absolute `…/magic-link/login?magicLinkToken=<otp>` URL with `ServletUriComponentsBuilder.fromCurrentContextPath()` — request thread — and emails it via `EmailService.sendMagicLinkEmail`. `MfaRedirectAuthenticationSuccessHandler` delegates to the same service when a *manual* login hits an unverified email, e.g. a user who never clicked the link and logs in again later.)
8. User clicks the link → `GET /magic-link/login?magicLinkToken=<otp>`. `AuthFlowController.forwardSpaShell` forwards to the SPA shell (explicit GET mapping — the path also has a `@PostMapping`, so it can't rely on the static fallback); the Nuxt `magic-link/login` page reads the token from `useRoute().query` (SPA mode — no hydration, the query string survives the full page load) and puts it in a hidden field of the **"Continue with login"** form (a native `<form method="post" action="/magic-link/login">`; no auto-submit).
9. The user clicks the button → `POST /magic-link/login` carrying `magicLinkToken`. `AuthFlowController.verifyMagicLink` binds it (`@RequestParam`), consumes it via `JdbcOneTimeTokenService` (a delete-and-return against the `one_time_tokens` table) and verifies the returned `username` matches the pending account's email, calls `UserCredentialService.verifyEmail` (`is_email_verified=true`), builds a full `MfaAuthenticationToken`, saves the session, and redirects to the saved OAuth2 request. (A missing/blank token or one that fails to consume → `redirect:/magic-link/login?e=invalid_token`, which the page displays via `errorMessages.ts`.)
10. If no SavedRequest exists (direct `/signup` visit), `verifyMagicLink` redirects to the web-client base URL (`web-client.location`, default `http://localhost:3000`) so web-client restarts `/oauth2/authorize` with its own `state` (the auth-server can't initiate it — web-client validates `state` against its own `sessionStorage`). Limitation: same-browser only — the pending token and the saved request live in that one HTTP session. Requiring a button click (rather than auto-submitting on mount) means an email client/scanner that only prefetches the link cannot trigger the consuming POST.

| Class | Role |
|---|---|
| `AuthFlowController.signup` (`controller/`) | `POST /signup` — creates the account, establishes the pending session, issues the magic link, redirects to `/signup/success` (errors: `e=invalid_request` / `e=email_taken` back to `/signup` with name/email prefill) |
| `MagicLinkService` (`service/`) | `issueAndEmail(email)` — generates the magic-link token (`JdbcOneTimeTokenService`), builds the absolute URL from the current request, emails it; shared by `signup()` and `MfaRedirectAuthenticationSuccessHandler` |
| `Validator` (`validator/`) | Server-side field validation (`validateCreateAccountRequest`) and the shared password policy (`validatePassword`); throws `InvalidRequestException` |
| `RoleRepository` (`repository/`) | `insert(credentialId, roleName)` — role row with a generated `role_guid` |
| `MfaAwareDaoAuthenticationProvider` (`component/`) | Unverified email on a *manual* login → `CreateAccountPendingAuthenticationToken` |
| `CreateAccountPendingAuthenticationToken` (`principal/`) | First factor passed, email unverified; `isAuthenticated()=false` |
| `AuthFlowController.verifyMagicLink` (`controller/`) | `POST /magic-link/login` — binds `magicLinkToken` (`@RequestParam`, posted by the Nuxt page from the link's query string), consumes it, verifies email, upgrades session, redirects to saved request or web-client |
| `InvalidRequestException` / `EmailAlreadyExistsException` (`exception/`) | Caught by `signup()` and turned into the error redirects above (`GlobalExceptionHandler` remains as a JSON backstop for REST endpoints) |

`UserCredentialService` also gains `createAccount(request)`, `verifyEmail(email)`, and `isEmailVerified(email)`; `UserCredentialRepository` gains `insert(...)` and `verifyEmail(...)`; and the `UserCredential` record gains a `name` field (its `RowMapper` and `findByEmail` query are updated accordingly).

#### Forgot-Password / Reset Flow

A self-service password reset that **doubles as the second factor** for that login (it replaces the OTT step, not adds to it). Gated by `is_password_change_required` (boolean column on `user_credential`, default `false`; mirrored as `UserCredential.passwordChangeRequired`).

1. The login page (`LoginForm.vue`) links to `/forgot-password` ("Forgot?"). That page mounts `ForgotPasswordForm.vue`, which validates the email client-side (vee-validate + yup, must contain `@`) and `fetch`-POSTs `{ email }` to `POST /api/temp-password`.
2. `TempPasswordController` → `UserCredentialService.requestTempPassword(email)` (`@Transactional`): if the email matches an account, it generates a **`SecureRandom`, complexity-compliant** temporary password (12 chars, guaranteed upper+lower+digit), overwrites the `password` column with its bcrypt hash (**the original password is destroyed**), and sets `is_password_change_required=true`. It returns the plaintext temp password (for emailing) or `null` if no match. The controller then calls `EmailService.sendTempPasswordEmail(to, tempPassword)`.
3. The endpoint **always returns 200** regardless of whether the email exists — account existence is never revealed (no enumeration). `ForgotPasswordForm` therefore ignores the outcome and (in a `finally`) `navigateTo({ path: '/login', query: { email, notice: 'tempPasswordSent' } })`; `LoginForm.vue` reads the query via `useRoute()` to **pre-fill the email** and show a success `v-snackbar`: "If the email provided matches one we have on file, you will receive an email with a temporary password."
4. The user logs in with the temp password. `MfaAwareDaoAuthenticationProvider` validates it and — **checking `isPasswordChangeRequired` first, before the email-verified and MFA checks** — returns a `PasswordChangePendingAuthenticationToken` (no authorities, `isAuthenticated()=false`). This first-position check is why the reset replaces, rather than stacks on top of, the OTT second factor.
5. `MfaRedirectAuthenticationSuccessHandler` detects that token and `sendRedirect("/reset-password")`. No token/email is minted here — the temp password was already emailed in step 2.
6. `GET /reset-password` forwards to the SPA shell via `AuthFlowController.forwardSpaShell` (unguarded, like the OTT/magic-link GETs). The page mounts `ResetPasswordForm.vue` — "New Password" / "Confirm New Password", validated with vee-validate against the **same rules as account creation** plus a confirm-match. On valid submit it submits a **hidden native `<form method=post action=/reset-password>`** carrying only `newPassword` (native form so the browser follows the final 302; confirm-match is client-side only, mirroring how account creation doesn't re-check its confirm field server-side).
7. `AuthFlowController.resetPassword` (`POST /reset-password`, `@RequestParam newPassword`) requires a `PasswordChangePendingAuthenticationToken` in the `SecurityContext` (else `redirect:/login`), then calls `UserCredentialService.completePasswordReset(email, newPassword)` (`@Transactional`): re-validates via `Validator.validatePassword` (failure → `redirect:/reset-password?e=invalid_password`), stores the new bcrypt password, sets `is_password_change_required=false`, **and sets `is_email_verified=true`**. It then upgrades the session to a full `MfaAuthenticationToken` and `redirect:`s to the saved OAuth2 request (or `web-client.location` if none).

**Why reset also marks the email verified:** receiving and correctly entering a temporary password that was emailed to the address proves the user controls that inbox — so a successful reset is itself proof of email ownership. Because `passwordChangeRequired` is checked *before* `isEmailVerified` in the provider, this also prevents an unverified account from getting stuck: an account that resets ends up fully verified rather than in an inconsistent state.

**Remember-me interaction:** no change is needed in `MfaAwareRememberMeAuthenticationProvider`. `TokenBasedRememberMeServices` signs its cookie with the password hash, so changing the password during a reset automatically invalidates any existing remember-me cookie.

| Class | Role |
|---|---|
| `TempPasswordController` (`controller/`) | `POST /api/temp-password` — always 200; on match, generate+persist+email a temp password. Also hosts the bearer-guarded `POST /api/temp-password/test` that returns the plaintext instead (see integration-test self-calls) |
| `TempPasswordRequest` (`dto/request/`) | `{ email }` body for the request above |
| `PasswordChangePendingAuthenticationToken` (`principal/`) | Temp password accepted, new password not yet set; `isAuthenticated()=false` |
| `AuthFlowController.resetPassword` (`controller/`) | `POST /reset-password` — verify the pending token, store the new password, clear the flag, verify email, upgrade session, redirect to saved request (the form GET forwards to the shell via `forwardSpaShell`) |
| `EmailService.sendTempPasswordEmail` (`service/`) | Emails the temp password (qa/prod), logs it at INFO (dev), or skips (test) |

`UserCredentialService` gains `requestTempPassword(email)`, `completePasswordReset(email, newPassword)`, and `isPasswordChangeRequired(email)`; `UserCredentialRepository` gains `updatePassword(email, encoded)` and `setPasswordChangeRequired(email, required)`; the `UserCredential` record gains a `passwordChangeRequired` field (its `RowMapper`, `findByEmail`, and `insert` are updated accordingly). Frontend additions: `pages/forgot-password/index.vue` + `ForgotPasswordForm.vue`, `pages/reset-password/index.vue` + `ResetPasswordForm.vue`, and the "Forgot?" link / email-prefill / snackbar in `LoginForm.vue`.

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
| `reuse-refresh-tokens` | `false` (seeded in `create_client_table.sql`) — every refresh exchange **rotates** the refresh token, so bff-server always stores a fresh one; a used or rejected refresh token can never succeed again. Note: `initialize_db/` scripts only run on first DB init, so an existing local DB needs a re-seed or manual `UPDATE` to pick this up |

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

In addition to being an Authorization Server, auth-server is **also an OAuth2 Resource Server** (`spring-boot-starter-oauth2-resource-server`). This exists so integration tests can drive flows that normally depend on reading an email (the MFA OTT, the account-creation magic link, and the forgot-password temp password) without an inbox: a test authenticates as `INTEGRATION_TEST_CLIENT` via `client_credentials`, then uses that access token to call test-only endpoints on auth-server itself that return the generated token/password value directly.

How it is wired in `config/SecurityConfig.java`:
- `@EnableMethodSecurity` enables `@PreAuthorize` on controller methods.
- The default `SecurityFilterChain` adds `oauth2ResourceServer(jwt)`. Because that chain pins an explicit `AuthenticationManager` (for form-login / remember-me / guest), the resource server's `BearerTokenAuthenticationFilter` delegates bearer-token authentication to that same `ProviderManager`. A `JwtAuthenticationProvider` (built from the in-memory `jwtDecoder`) is therefore added to the `ProviderManager` explicitly — without it, a bearer request fails with `ProviderNotFoundException: No AuthenticationProvider found for BearerTokenAuthenticationToken`.
- The `JwtAuthenticationConverter` maps the JWT `scope` claim to authorities with **no** prefix (mirroring simple-resource-server), so scope `INTEGRATION_TEST_CLIENT_WRITE` becomes the authority `INTEGRATION_TEST_CLIENT_WRITE`.

**Test-only endpoints (`MfaController`, `TempPasswordController`)** — each guarded by `@PreAuthorize("hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')")`, i.e. callable only with an `INTEGRATION_TEST_CLIENT` client_credentials access token:

| Endpoint | Purpose |
|---|---|
| `POST /ott/generate/test?email=<email>` | Mints the MFA OTT for the given `email` and returns the value in the response body (in the real flow the OTT is generated server-side by `MfaRedirectAuthenticationSuccessHandler` during login — there is no public generate endpoint). Stateless like the magic-link variant — the pending user **cannot** be read from the session here, because the bearer-authenticated caller's JWT context replaces the session context for the request. Verification stays session-bound: `POST /ott/login` checks the consumed token's username against the session's pending user |
| `POST /magic-link/generate/test?email=<email>` | Mints an account-creation magic-link token via `JdbcOneTimeTokenService` for the given `email` and returns the token value; stateless — the email is passed explicitly rather than read from the session, since a client_credentials caller has no browser session |
| `POST /api/temp-password/test` (body `{"email": …}`) | Like `POST /api/temp-password` but returns the plaintext temp password in the response body instead of emailing it. Same `UserCredentialService.requestTempPassword` underneath, so it has the same side effects: the stored password is overwritten with the temp password's hash and `is_password_change_required` is set to `true` |

**Self-testing pattern (client_credentials → call auth-server):**
1. `OAuth2Client.getClientCredentialsToken(...)` exchanges `INTEGRATION_TEST_CLIENT` credentials at `POST /oauth2/token` (`grant_type=client_credentials`) for an access token.
2. The test attaches that token as a `Bearer` header when calling a `/…/test` endpoint to obtain the OTT / magic-link token value directly.
3. The token value then feeds the normal verification endpoint (e.g. `POST /magic-link/login`) to complete the flow.

**Test source layout (`auth-server/src/test/java/com/roots/authserver/`):** support code is split by role. `client/` holds the HTTP clients: `AuthServerClient` (the "browser" — a cookie-bearing `HttpClient` for form-login/redirect flows, plus an internal cookie-less machine client for the bearer-token `/…/test` calls so they can't disturb the browser session; redirects are never auto-followed) and `AccountManagementClient` (`RestTemplate`-backed, bearer-only, lenient error handler so non-2xx comes back as a `ResponseEntity` instead of throwing). `dto/` holds the response records (`TokenResponse`, `CreateTestAccountResponse`, `UserCredentialTestingResponse`). `util/` holds `HttpFlowUtils` (Location resolution, query-param extraction, and `followRedirects`, which walks a 302 chain on the browser session until the Location reaches a target prefix — capped at 15 hops so an auth regression that produces a `/login` ↔ `/oauth2/authorize` redirect loop fails the test instead of hanging the suite). `integration/` holds `IntegrationTestBase`, `TestConfig`, `OAuth2Client` (token-endpoint exchanges: client_credentials and authorization_code), and the test classes.

**Cross-service account fixtures:** tests that need a pre-existing account do **not** drive auth-server's own signup flow — they call **account-management's** `/api/account/test` endpoints via `AccountManagementClient` to create an account with exactly the flag combination under test (`mfaEnabled`/`emailVerified`/`passwordChangeRequired`/`roles`; the no-roles overload passes an empty list and account-management's `MEMBER` floor applies), then delete it by `userGUID` in `@AfterEach`. Reading state back mid-flow (e.g. the stored bcrypt hash, or `is_email_verified` before/after verification) uses `GET /api/account/test`. Consequently the auth-server integration suite requires **both** auth-server and account-management running; `account-management-location` (default `http://localhost:8082`) sits alongside `auth-server-location` in `src/test/resources/application.yml`. Emails are randomized per test (`itest…` + UUID + `@example.com`) so runs never collide with leftover rows.

**Integration test classes (`integration/`):**

| Class | Flow under test |
|---|---|
| `GuestLoginIntegrationTest` | `POST /login/guest`: starts `/oauth2/authorize` (so a SavedRequest exists), logs in as guest, follows the 302 chain to the web-client callback, exchanges the code, and asserts the JWT claims (`roles` contains `GUEST`; scopes `openid`/`WEB_CLIENT_READ`). No account fixture — guest is synthetic |
| `CreateAccountIntegrationTest` | The full signup chain: form `POST /signup` 302s straight to `/signup/success` (account created + pending session in one request; email unverified) → `POST /magic-link/generate/test` → `POST /magic-link/login` → lands on the callback with an authorization code; asserts `emailVerified` flipped via `GET /api/account/test` |
| `ForgotPasswordIntegrationTest` | Creates an account with `passwordChangeRequired=true` (`@BeforeEach`), mints a temp password via `POST /api/temp-password/test`, logs in with it (302 → `/reset-password`), posts the new password, then reads the stored hash back and asserts only the new password `matches()` it |
| `LoginIntegrationTest` | Form-login variants against an account with default roles, MFA off, email verified, no password change required. Happy path: starts `/oauth2/authorize`; `POST /login` (asserting the immediate 302 returns to the saved authorize request, not a pending step); follows the chain to the callback; exchanges the code; asserts `sub` = the account email and `roles` contains `MEMBER`; then clears session cookies and asserts a fresh authorize flow **dead-ends on the login form** (200 `/login`) since no remember-me cookie exists. Remember-me: logs in with `remember-me=true`, asserts the persistent `remember-me` cookie is issued on the login response, clears the session cookies (`AuthServerClient.clearSessionCookies()` — a "browser restart" that keeps only persistent cookies), and proves a fresh authorize flow completes from the cookie alone (302 to `/login` → remember-me filter authenticates → back to the saved request → callback; `sub` = the account email). An MFA-enabled nested group covers the remember-me × "Remember this browser?" matrix — every test asserts the first factor lands on `/ott/login` (not the callback), obtains the OTT via `POST /ott/generate/test`, submits `POST /ott/login`, checks `is_mfa_enabled` in the DB, then "restarts the browser" and logs in again: no cookie + browser not remembered → second login is gated by the MFA page again; no cookie + remembered → `is_mfa_enabled` flips `false`, second credential login completes with no OTT; cookie + not remembered → the cookie satisfies the first factor (no login form) but the chain dead-ends on the OTT page, and a fresh OTT completes it; cookie + remembered → the second flow runs straight to the callback with no interaction at all (`sub` verified after code exchange). An `InvalidCredentials` nested group asserts a wrong password and an unknown email both 302 to the identical `/login?e=invalid_login` (anti-enumeration) |
| `NegativeCaseIntegrationTest` | `@Nested` groups: `POST /signup` input rejection (parameterized over each `Validator` branch → 302 with `e=invalid_request`; duplicate email → 302 with `e=email_taken`) and bearer authorization on the `/…/test` endpoints (missing / wrong-scope / malformed token → 401/403) |

**Account variants in `LoginIntegrationTest`:** the account-creating `@BeforeEach` lives in a `@Nested` class per flag combination (currently `MfaDisabled_EmailVerified_PasswordChangeNotRequired` and `MfaEnabled_EmailVerified_PasswordChangeNotRequired`), so tests that share an account shape share its setup, while future combinations (unverified email, …) get their own nested group. The outer class holds the `email`/`userGUID` fields and the `@AfterEach` delete, which is null-guarded — a test that fails before its nested setup runs has nothing to clean up.

See `auth-server/README.md` for live-server run instructions.

**Per-test HTTP client lifecycle:** the integration test classes extend `IntegrationTestBase`, whose `@BeforeEach` builds a fresh `OAuth2Client`, exchanges `INTEGRATION_TEST_CLIENT` credentials for a client_credentials access token (scopes `INTEGRATION_TEST_CLIENT_WRITE READ DELETE`), and hands that token to a fresh `AuthServerClient` and `AccountManagementClient`. `@AfterEach` `close()`s the two `AutoCloseable` ones (`AuthServerClient.close()` shuts down both its cookie-bearing and cookie-less `HttpClient`s; `AccountManagementClient` is `RestTemplate`-backed — no pooled connections to reap, nothing to close). This replaced an earlier design where the clients were shared singleton `@Bean`s in `TestConfig`. *Before:* the cached Spring test context shared one `HttpClient` (one connection pool) across the whole suite; on long runs an idle pooled keep-alive connection outlived Tomcat's 20 s `keepAliveTimeout`, the server closed it, the client reused the dead connection, and the request failed with `ClosedChannelException` surfaced as `ConnectException` (the *second* test class to run failed; each passed in isolation). *After:* a fresh client per test means a fresh connection pool per test — no connection is idle long enough to be reaped, none is shared across tests, and each test gets a clean session. `TestConfig` now only anchors `@TestPropertySource`/`@Value`; its sole bean is the delegating `PasswordEncoder` used to verify stored hashes. **Do not** reintroduce shared client beans.

### web-client vs auth-server/frontend

- `web-client/` — standalone Nuxt 4 app, developed and deployed independently
- `auth-server/frontend/` — Nuxt app embedded inside auth-server's Spring Boot JAR via Maven build

### auth-server/frontend structure

```
auth-server/frontend/
├── app.vue                          # root layout wrapper
├── components/
│   ├── LoginForm.vue                # contains two forms: #login-form (POST /login — email, password, remember-me; intercepted by Spring Security's UsernamePasswordAuthenticationFilter) and #guest-form (POST /login/guest — no fields); Vuetify inputs use the HTML `form` attribute to bind to the correct form; "Continue as Guest" button submits #guest-form; links to /signup and /forgot-password; pre-fills email / shows a snackbar from the forgot-password query params; shows a v-alert for server error codes (useServerErrorMessage)
│   ├── OttLoginForm.vue             # native HTML form (POST /ott/login); OTT text field + "Remember this browser?" checkbox (posts rememberBrowser=true); shows a v-alert for server error codes (e.g. invalid_token)
│   ├── SignupForm.vue               # account-creation form (name, email, password, confirm password); Vuetify fields bind to a native form (POST /signup) via the HTML `form` attribute; vee-validate + yup intercept the submit and resume the native submission when valid (confirm field client-side only, not posted); prefills name/email from the error-redirect query; shows a v-alert for server error codes (email_taken, invalid_request) via useServerErrorMessage; links to /login
│   ├── ForgotPasswordForm.vue       # email field (vee-validate + yup); POSTs { email } to /api/temp-password, always navigates back to /login with prefill+notice query
│   ├── ResetPasswordForm.vue        # new/confirm password (same rules as signup + confirm-match); submits hidden native form (POST /reset-password, newPassword only); shows a v-alert for e=invalid_password
│   └── MagicLinkLoginForm.vue       # "Continue with login" card: reads magicLinkToken from useRoute().query and posts it as a hidden field (native form POST /magic-link/login); the button click (not a prefetchable GET) consumes the token; shows a v-alert for e=invalid_token
├── composables/
│   └── useServerErrorMessage.ts     # reads ?e=<code> from the route, maps it via utils/errorMessages.ts, scrubs the code from the URL after mount (flash semantics)
├── utils/
│   └── errorMessages.ts             # error-code → display-text map (invalid_login, invalid_token, invalid_password, oauth_redirect_failed, no_mfa_pending, email_taken, invalid_request); the server only ever sends codes
├── pages/
│   ├── index.vue                    # / — client-side navigateTo('/login', { replace: true }) (routeRules can't redirect: no Nitro at runtime)
│   ├── login.vue                    # /login — mounts LoginForm centered on page
│   ├── about.vue                    # /about
│   ├── signup/
│   │   ├── index.vue                # /signup — mounts SignupForm centered on page
│   │   └── success.vue              # /signup/success — "Account creation succeeded. Please check your email to continue." (reachable by direct URL; no route guard)
│   ├── forgot-password/
│   │   └── index.vue                # /forgot-password — mounts ForgotPasswordForm
│   ├── reset-password/
│   │   └── index.vue                # /reset-password — mounts ResetPasswordForm
│   ├── magic-link/
│   │   └── login.vue                # /magic-link/login — mounts MagicLinkLoginForm centered on page
│   └── ott/
│       └── login.vue                # /ott/login — mounts OttLoginForm centered on page (no API call: the OTT was already generated and emailed/logged server-side by MfaRedirectAuthenticationSuccessHandler before the redirect here)
├── tests/                           # Vitest unit tests — no backend needed (see "Frontend unit tests" under Commands)
│   ├── setup.ts                     # visualViewport shim (Vuetify overlays need it under happy-dom)
│   ├── testUtils.ts                 # flushAsync — drains vee-validate + vue-router async work a plain flushPromises misses
│   ├── components/                  # one spec per component: form contract (action/method/field names/hidden fields) + server-redirect rendering (?e=…, prefill, notice) + client-side validation gating (invalid input blocks the native submit/fetch, valid input resumes it — HTMLFormElement.prototype.submit is spied)
│   ├── composables/                 # useServerErrorMessage: code→message mapping + URL scrub (flash) semantics
│   └── pages/                       # callback (every Google-exchange branch, fetch mocked) and index (redirect)
├── vitest.config.ts                 # defineVitestConfig — environment: 'nuxt' (auto-imports/Vuetify work in tests), happy-dom
├── nuxt.config.ts                   # ssr: false — pure SPA; one shell, no hydration, query params readable everywhere
└── package.json
```

UI uses **Vuetify 4** (`vuetify-nuxt-module`). `/` redirects to `/login` client-side via `pages/index.vue`. The signup form validates client-side with **vee-validate** + **yup** (`@vee-validate/yup`).

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

The auth-server must include a `roles` claim in issued JWTs (uppercase values, e.g. `PASTOR`) for role checks to pass. This is implemented via `OAuth2TokenCustomizer` in `config/SecurityConfig.java`. The same customizer also enriches the **id_token** with `email` (the principal name), `roles`, and `userGUID` (looked up via `UserCredentialRepository.findByEmail`) — these are the claims bff-server serves from `GET /api/auth/status`. A guest principal has no `user_credential` row, so its id_token carries no `userGUID` claim (email is the literal `guest`, roles `[GUEST]`).

### account-management

A second **OAuth2 Resource Server** (Spring Security 7.x, port `8082`), wired the same way as simple-resource-server: it validates JWT bearer tokens issued by auth-server, runs `anyRequest().permitAll()` at the filter chain level, and enforces access purely via `@PreAuthorize` (`@EnableMethodSecurity`). `config/SecurityConfig.java` defines the `SecurityFilterChain`, a delegating `PasswordEncoder` bean (`{bcrypt}`), and a `JwtAuthenticationConverter` that maps the JWT `scope` claim to authorities with **no** prefix and the `roles` claim to `ROLE_*`.

Unlike simple-resource-server, it owns no schema of its own — it reads and writes the **shared auth-server DB** (`user_credential` and `role` tables, MySQL on port `3307`) directly via `JdbcTemplate`. It uses the same `MYSQL_AUTH_SERVER_*` env vars as auth-server (`MYSQL_AUTH_SERVER_DB_URL` defaults to `jdbc:mysql://localhost:3307/auth-server-db`; `MYSQL_AUTH_SERVER_ROOT_USERNAME`/`MYSQL_AUTH_SERVER_ROOT_PASSWORD` required, no defaults). JWK URI defaults to `${AUTH_SERVER_JWK_URI:http://localhost:9000/oauth2/jwks}` (fetched lazily on first authenticated request). Config lives in `src/main/resources/application.yaml`.

**Endpoints (`controller/AccountController`)** under `/api/account`. Most are integration-test-only — callable only with an `INTEGRATION_TEST_CLIENT` `client_credentials` access token (the same machine client auth-server seeds in `create_client_table.sql`) — and exist so integration tests across the stack can create, read, and tear down accounts directly in the shared DB without driving the full signup/email flow. The one exception is `GET /api/account`, which is **public** (no `@PreAuthorize`) and returns only non-sensitive fields:

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /api/account/test` | `@PreAuthorize hasAuthority('INTEGRATION_TEST_CLIENT_WRITE')` | Creates an account with caller-supplied `mfaEnabled`/`emailVerified`/`passwordChangeRequired`/`roles`; returns **201** `CreateAccountResponse(name, email, userGUID, mfaEnabled, emailVerified, passwordChangeRequired, roles)` |
| `GET /api/account/test?email=…` *or* `?userGUID=…` | `@PreAuthorize hasAuthority('INTEGRATION_TEST_CLIENT_READ')` | Reads **every** field of an account by **exactly one** of email/userGUID; returns **200** `UserCredentialTestingResponse(userGUID, email, name, password, mfaEnabled, emailVerified, passwordChangeRequired)` — includes the bcrypt `password`; internal surrogate `id` omitted. **404** if no match |
| `GET /api/account?email=…` *or* `?userGUID=…` | **public** (no `@PreAuthorize`) | Reads only non-sensitive fields by **exactly one** of email/userGUID; returns **200** `UserCredentialResponse(email, userGUID, mfaEnabled)`. **404** if no match |
| `DELETE /api/account/test?email=…` *or* `?userGUID=…` | `@PreAuthorize hasAuthority('INTEGRATION_TEST_CLIENT_DELETE')` | Deletes by **exactly one** of email/userGUID; returns **204**. Idempotent — no match is a no-op so teardown can run repeatedly |

Request/response and flow details:
- `dto/request/CreateAccountRequest(name, email, password, mfaEnabled, emailVerified, passwordChangeRequired, roles)` — the compact constructor defaults `mfaEnabled=true`, `emailVerified=false`, and `passwordChangeRequired=false` when omitted/null (Jackson uses the canonical constructor).
- `validator/Validator` re-validates server-side: name required and ≤ 255; email required and contains `@`; password required, ≥ 8 chars with at least one uppercase, lowercase, and digit → `InvalidRequestException` (**400**). `validateAccountLookup(email, userGUID)` enforces exactly one of email/userGUID (not both, not neither) → also **400**; it is shared by the two GET reads and the DELETE.
- `service/AccountService.createTestAccount()` (`@Transactional`) rejects a duplicate email with `EmailAlreadyExistsException` (**409**), hashes the password via the `PasswordEncoder`, generates a `user_guid` UUID, inserts the credential (`UserCredentialRepository.insert`, id via `GeneratedKeyHolder`), and inserts roles. `resolveRoles()` always includes `MEMBER` (the floor) plus any requested roles, de-duplicated preserving order. `deleteTestAccount()` deletes role rows before the credential (the role FK has no `ON DELETE CASCADE`).
- `service/AccountService.getUserCredentialByEmail(email)` / `getUserCredentialByUserGUID(userGUID)` (both `@Transactional(readOnly = true)`) return the full `UserCredential` (all columns, via the existing `UserCredentialRepository` finders) or throw the **checked** `UserCredentialNotFoundException` when no row matches. The controller maps the result to `UserCredentialTestingResponse` (all fields) or `UserCredentialResponse` (email/userGUID/mfaEnabled) via each DTO's static `from(UserCredential)`.
- `enums/Role` — `PASTOR`, `DEACON`, `SMALL_GROUP_LEADER`, `VICE_SMALL_GROUP_LEADER`, `MEMBER`, `GUEST`; serialized to/from its lowercase `value` (`@JsonValue`/`@JsonCreator`, case-insensitive), e.g. `member`, `small_group_leader`.
- `exception/GlobalExceptionHandler` (`@RestControllerAdvice`) maps `InvalidRequestException` → **400**, `EmailAlreadyExistsException` → **409**, and `UserCredentialNotFoundException` → **404**, all as `{"error": "<message>"}`.
- **Swagger UI** is available via `springdoc-openapi-starter-webmvc-ui`; the endpoints carry `@Operation`/`@Parameter` annotations.

**Integration tests (`src/test/java/.../integration/`)** require both auth-server and account-management running. `AccountLifecycleIntegrationTest` obtains a `client_credentials` token (scopes `INTEGRATION_TEST_CLIENT_WRITE INTEGRATION_TEST_CLIENT_DELETE`) from auth-server via `OAuth2Client`, then drives create→delete (by email, and by userGUID) against account-management via `AccountManagementClient` (cookie-less; Bearer-token only). `TestConfig` builds the two clients as beans from `auth-server-location`/`account-management-location` in `src/test/resources/application.yml`. CI boots both services and runs these tests (see below).

### account-management endpoints summary

| Endpoint | Required authority |
|---|---|
| `POST /api/account/test` | `INTEGRATION_TEST_CLIENT_WRITE` scope |
| `GET /api/account/test` | `INTEGRATION_TEST_CLIENT_READ` scope |
| `GET /api/account` | none (public) |
| `DELETE /api/account/test` | `INTEGRATION_TEST_CLIENT_DELETE` scope |

### bff-server

The **backend-for-frontend** (port `8083`, override `SERVER_PORT`). End goal: manage OAuth2 tokens on behalf of web-client so tokens no longer live in the browser. The browser holds only the `SESSION` cookie; tokens live in Redis keyed by the session id. First real endpoint is in place (`GET /api/auth/status`, below); the authorization-code callback that writes the *initial* tokens to Redis is still in web-client, so in real traffic the endpoint currently answers `isLoggedIn=false` until that move lands.

**Token store (Redis).** Each session's values live under plain string keys — `<sessionId>:access_token`, `<sessionId>:refresh_token`, `<sessionId>:id_token`, plus the short-lived `<sessionId>:oauth_state` (`TokenStoreService`; `<sessionId>` is the Spring Session id, i.e. the base64-decoded `SESSION` cookie). Each key carries its own TTL: JWTs get TTL = their own `exp` minus now, the opaque refresh token gets `token-store.refresh-token-ttl-seconds` (default `3600`, matching auth-server's `refresh-token-time-to-live`). Redis therefore expires each token exactly when the token itself does — an absent key *is* the expiry check, which is why reads never look at `exp`.

**`GET /api/auth/status`** (`AuthController` → `AuthStatusService`; always 200 — "not logged in" is a normal answer):
1. `<sessionId>:id_token` present → logged in. Decode its payload (no signature check — the bff itself stored it, and it originally came from auth-server over a server-to-server call) and return `{isLoggedIn: true, email, userGUID, roles}` (`LoginStatusResponse`, `NON_NULL` so a logged-out answer is just `{"isLoggedIn": false}`; a guest login has no `userGUID` field).
2. No id_token but `<sessionId>:refresh_token` present → `AuthServerTokenClient` performs the `refresh_token` grant against `POST {auth-server.internal-location}/oauth2/token`, authenticating as **WEB_CLIENT** (refresh tokens are client-bound, so the bff must use the same registered client web-client's codes are issued for). Success → store all three fresh tokens (rotation means the refresh token is always new) and return the claims.
3. Exchange fails (rotated-away, expired, revoked, or garbage token → 400) → delete `<sessionId>:refresh_token` and return `isLoggedIn=false`.
4. Neither key → `isLoggedIn=false`.

**`GET /api/auth/authorize`** (`AuthController` → `AuthorizeService`): kicks off the authorization-code flow with an unconditional **302** to `{auth-server.external-location}/oauth2/authorize?response_type=code&client_id={web.client.id}&redirect_uri={web.client.origin}/callback&scope=openid WEB_CLIENT_READ&state=<uuid>`. The bff mints the `state` and stores it at `<sessionId>:oauth_state` (TTL 5 min) — the future bff callback validates against it; web-client's own state logic goes away when it is repointed here (a later step; in the interim nothing calls this endpoint in production). No logged-in short-circuit: an already-authenticated auth-server session just completes the flow silently. `auth-server.external-location` (`AUTH_SERVER_EXTERNAL_LOCATION`, default `http://localhost:9000`) exists because this Location is followed by the **browser**, outside the docker network where `auth-server:9000` (the RestClient's internal location) doesn't resolve — compose leaves it at the default.

Config: `auth-server.internal-location` (`AUTH_SERVER_INTERNAL_LOCATION`, default `http://localhost:9000`; compose sets `http://auth-server:9000`), `auth-server.external-location` (above), `web.client.id` (`WEB_CLIENT_ID`, default `WEB_CLIENT`), and `web.client.secret` (**`WEB_CLIENT_SECRET`, required, no default** — must match the `{noop}secret` seed; compose passes it through, CI hardcodes `secret` since the seeded value is already public in the checked-in SQL). **Boot 4 gotcha #2:** the `RestClient.Builder` bean ships in `spring-boot-starter-restclient`, not in `spring-boot-starter-web`.

Current state otherwise remains scaffolding:

- **Spring Security** (`config/SecurityConfig.java`): `anyRequest().permitAll()`; CSRF disabled (project convention — revisit when state-changing BFF endpoints land, since the SESSION cookie will be the browser's only credential); `SessionCreationPolicy.ALWAYS` — every request eagerly gets a session, because the session is where tokens will be held.
- **Spring Session Redis** (`spring-boot-starter-session-data-redis`): HTTP sessions live in Redis (`spring:session:sessions:*` keys); the servlet `JSESSIONID` is replaced by the `SESSION` cookie. **Boot 4 gotcha:** session auto-config ships in Boot's own `spring-boot-session-data-redis` module — depending on the plain `org.springframework.session:spring-session-data-redis` jar alone silently leaves the in-memory container session in place; use the starter.
- **CORS**: only `web.client.origin` (default `http://localhost:3000`, override `WEB_CLIENT_ORIGIN`) is allowed, with `allowCredentials=true` so the browser can send the `SESSION` cookie cross-origin; a preflight from any other origin gets 403.
- **Redis**: the `bff-server-redis` compose service (`redis:8`, no AUTH — dev/CI grade, like the DB), port `6379` published so a locally-run bff-server reaches it at `localhost:6379`. bff-server reads `${REDIS_HOST:localhost}`/`${REDIS_PORT:6379}`; compose sets `REDIS_HOST=bff-server-redis`. In compose, `bff-server` `depends_on` both `bff-server-redis` and `auth-server` (healthy).
- **Integration tests** (`integration/`; need live bff-server + Redis + auth-server): `AuthStatusIntegrationTest` covers all four `/api/auth/status` paths: it mints **genuine tokens via a real guest login**, derives its own session id by base64-decoding its `SESSION` cookie, and seeds/asserts token keys through the autowired `TestTokenStoreService` — a test-side counterpart of the main store (same keys, connected via the published Redis port, extended with TTL reads and bulk teardown), defined as a bean in `TestConfig` so the cached test context shares one Lettuce connection across the suite (the per-test-fresh rule is for the HTTP clients, whose pooled keep-alive connections the server reaps; Lettuce reconnects itself). Asserts: id_token hit returns guest claims; refresh-token-only revives the login, stores id/access tokens with real TTLs, and rotates the stored refresh token; nothing stored → `isLoggedIn=false` with claim fields omitted; a rejected refresh token → `isLoggedIn=false` and the key deleted. `TestConfig` anchors the context and defines the Redis test beans (`LettuceConnectionFactory` → `StringRedisTemplate` → `TestTokenStoreService`); connection targets live in `src/test/resources/application.yml`. `AuthorizeIntegrationTest` covers `GET /api/auth/authorize` twice over: a contract test (raw 302 — every authorize query param, and the minted `state` sitting at `<sessionId>:oauth_state` with a positive TTL) and an acceptance test (plays the browser through the emitted Location via a real guest login and asserts the web-client callback receives a `code` plus exactly the bff-held `state` — proof auth-server accepts the bff-built URL). HTTP contact goes through per-server client classes in the test `client/` package (mirroring auth-server's test layout), each owning and configuring its own `HttpClient`(s), `AutoCloseable`, built fresh per test: `BffClient` (`getLoginStatus`, `getAuthorize` — GETs with an optional replayed SESSION cookie) and `AuthServerClient` (`fetchGuestTokens` — the guest authorize→login→code-exchange flow, a slimmed port of auth-server's own test client; guest needs no account fixture yet yields all three tokens — and `completeGuestLogin(authorizeUrl)`, which drives the same login from an externally built authorize URL and returns the final callback URL). There is deliberately no host-run `contextLoads` test (same reasoning as account-management: the healthy container is the context-load proof).

See `bff-server/README.md` for run instructions and the full scaffolding rationale.

### bff-server

The **backend-for-frontend** (port `8083`, override `SERVER_PORT`). End goal: manage OAuth2 tokens on behalf of web-client so tokens no longer live in the browser. The browser holds only the `SESSION` cookie; tokens live in Redis keyed by the session id. First real endpoint is in place (`GET /api/auth/status`, below); the authorization-code callback that writes the *initial* tokens to Redis is still in web-client, so in real traffic the endpoint currently answers `isLoggedIn=false` until that move lands.

**Token store (Redis).** Each session's tokens live under three plain string keys — `<sessionId>:access_token`, `<sessionId>:refresh_token`, `<sessionId>:id_token` (`TokenStoreService`; `<sessionId>` is the Spring Session id, i.e. the base64-decoded `SESSION` cookie). Each key carries its own TTL: JWTs get TTL = their own `exp` minus now, the opaque refresh token gets `token-store.refresh-token-ttl-seconds` (default `3600`, matching auth-server's `refresh-token-time-to-live`). Redis therefore expires each token exactly when the token itself does — an absent key *is* the expiry check, which is why reads never look at `exp`.

**`GET /api/auth/status`** (`AuthController` → `AuthStatusService`; always 200 — "not logged in" is a normal answer):
1. `<sessionId>:id_token` present → logged in. Decode its payload (no signature check — the bff itself stored it, and it originally came from auth-server over a server-to-server call) and return `{isLoggedIn: true, email, userGUID, roles}` (`LoginStatusResponse`, `NON_NULL` so a logged-out answer is just `{"isLoggedIn": false}`; a guest login has no `userGUID` field).
2. No id_token but `<sessionId>:refresh_token` present → `AuthServerTokenClient` performs the `refresh_token` grant against `POST {auth-server.location}/oauth2/token`, authenticating as **WEB_CLIENT** (refresh tokens are client-bound, so the bff must use the same registered client web-client's codes are issued for). Success → store all three fresh tokens (rotation means the refresh token is always new) and return the claims.
3. Exchange fails (rotated-away, expired, revoked, or garbage token → 400) → delete `<sessionId>:refresh_token` and return `isLoggedIn=false`.
4. Neither key → `isLoggedIn=false`.

Config: `auth-server.location` (`AUTH_SERVER_LOCATION`, default `http://localhost:9000`; compose sets `http://auth-server:9000`), `web.client.id` (`WEB_CLIENT_ID`, default `WEB_CLIENT`), and `web.client.secret` (**`WEB_CLIENT_SECRET`, required, no default** — must match the `{noop}secret` seed; compose passes it through, CI hardcodes `secret` since the seeded value is already public in the checked-in SQL). **Boot 4 gotcha #2:** the `RestClient.Builder` bean ships in `spring-boot-starter-restclient`, not in `spring-boot-starter-web`.

Current state otherwise remains scaffolding:

- **Spring Security** (`config/SecurityConfig.java`): `anyRequest().permitAll()`; CSRF disabled (project convention — revisit when state-changing BFF endpoints land, since the SESSION cookie will be the browser's only credential); `SessionCreationPolicy.ALWAYS` — every request eagerly gets a session, because the session is where tokens will be held.
- **Spring Session Redis** (`spring-boot-starter-session-data-redis`): HTTP sessions live in Redis (`spring:session:sessions:*` keys); the servlet `JSESSIONID` is replaced by the `SESSION` cookie. **Boot 4 gotcha:** session auto-config ships in Boot's own `spring-boot-session-data-redis` module — depending on the plain `org.springframework.session:spring-session-data-redis` jar alone silently leaves the in-memory container session in place; use the starter.
- **CORS**: only `web.client.origin` (default `http://localhost:3000`, override `WEB_CLIENT_ORIGIN`) is allowed, with `allowCredentials=true` so the browser can send the `SESSION` cookie cross-origin; a preflight from any other origin gets 403.
- **Redis**: the `bff-server-redis` compose service (`redis:8`, no AUTH — dev/CI grade, like the DB), port `6379` published so a locally-run bff-server reaches it at `localhost:6379`. bff-server reads `${REDIS_HOST:localhost}`/`${REDIS_PORT:6379}`; compose sets `REDIS_HOST=bff-server-redis`. In compose, `bff-server` `depends_on` both `bff-server-redis` and `auth-server` (healthy).
- **Integration tests** (`integration/`; need live bff-server + Redis + auth-server): `SessionSmokeIntegrationTest` asserts `/actuator/health` is 200/UP, a `SESSION` cookie is issued on the first request (the ALWAYS policy), and replaying that cookie does not mint a new session (the round trip through the Redis store). `AuthStatusIntegrationTest` covers all four `/api/auth/status` paths: it mints **genuine tokens via a real guest login**, derives its own session id by base64-decoding its `SESSION` cookie, and seeds token keys directly into Redis via the published port (host-side `LettuceConnectionFactory`). Asserts: id_token hit returns guest claims; refresh-token-only revives the login, stores id/access tokens with real TTLs, and rotates the stored refresh token; nothing stored → `isLoggedIn=false` with claim fields omitted; a rejected refresh token → `isLoggedIn=false` and the key deleted. `TestConfig` (empty `@Configuration`) anchors the context; connection targets live in `src/test/resources/application.yml`. HTTP contact goes through per-server client classes in the test `client/` package (mirroring auth-server's test layout), each owning and configuring its own `HttpClient`(s), `AutoCloseable`, built fresh per test: `BffClient` (`getLoginStatus` — GET `/api/auth/status` with an optional replayed SESSION cookie) and `AuthServerClient` (`fetchGuestTokens` — the guest authorize→login→code-exchange flow, a slimmed port of auth-server's own test client; guest needs no account fixture yet yields all three tokens). There is deliberately no host-run `contextLoads` test (same reasoning as account-management: the healthy container is the context-load proof).

See `bff-server/README.md` for run instructions and the full scaffolding rationale.

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
npm run test               # frontend unit tests (Vitest + @nuxt/test-utils; no backend needed)
npm run test:watch         # watch mode
```

**Frontend unit tests** live in `frontend/tests/` and run entirely without a backend: native-form components are asserted against their outgoing form contract and against the query params the server 302s back with (`?e=…`, prefill, `notice=…`); `fetch`-based flows (forgot-password, the Google `/callback` code exchange) run against mocked `fetch`; `navigateTo` is mocked via `mockNuxtImport`. They are the first (fail-fast) step of `auth-server-ci.yml`. They are **not** part of the Maven build — run them via npm.

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

# Start the Redis instance backing bff-server's sessions (no env vars needed)
docker compose up -d bff-server-redis
```

## Key Configuration

- `auth-server/src/main/resources/application.yml` — server port defaults to `${SERVER_PORT:9000}`; `MYSQL_AUTH_SERVER_ROOT_USERNAME` and `MYSQL_AUTH_SERVER_ROOT_PASSWORD` are required with no fallback; `MYSQL_AUTH_SERVER_DB_URL` defaults to `jdbc:mysql://localhost:3307/auth-server-db`; Gmail SMTP is configured under `spring.mail` — `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD` have no defaults and are required in every profile (the `JavaMailSender` is auto-configured in all profiles, and the Actuator mail health indicator connects to SMTP on each health poll); uses `smtp.gmail.com:587` with STARTTLS; `web-client.location` defaults to `http://localhost:3000` (override: `WEB_CLIENT_LOCATION`) — web-client hand-off target after magic-link verification when no saved request exists; `google.client-id` (override: `GOOGLE_CLIENT_ID`, safe public default) — expected `aud` for Google id_token verification
- `auth-server/frontend/nuxt.config.ts` — `runtimeConfig.public.googleClientId` defaults to the dev Google OAuth client id (override: `NUXT_PUBLIC_GOOGLE_CLIENT_ID`); `runtimeConfig.public.googleClientSecret` has no default and must be set via `NUXT_PUBLIC_GOOGLE_CLIENT_SECRET` — **at frontend build time, not server runtime**: the SPA is statically generated, so the value is baked into the JS bundle by `npm run generate` (set it in the shell running the Maven build, or in the gitignored `auth-server/frontend/.env`); changing it requires a rebuild (see "Required env vars at startup" above)
- `simple-resource-server/src/main/resources/application.yml` — port defaults to `8081` (override: `SERVER_PORT`); JWK URI defaults to `http://localhost:9000/oauth2/jwks` (override: `AUTH_SERVER_JWK_URI`); CORS origin defaults to `http://localhost:3000` (override: `WEB_CLIENT_ORIGIN` via `web.client.origin` property)
- `bff-server/src/main/resources/application.yml` — port defaults to `8083` (override: `SERVER_PORT`); Redis at `${REDIS_HOST:localhost}:${REDIS_PORT:6379}` (Spring Session + token store); CORS origin defaults to `http://localhost:3000` (override: `WEB_CLIENT_ORIGIN` via `web.client.origin`); `auth-server.location` defaults to `http://localhost:9000` (override: `AUTH_SERVER_LOCATION`); `web.client.id` defaults to `WEB_CLIENT` (override: `WEB_CLIENT_ID`); `web.client.secret` is **required with no default** (`WEB_CLIENT_SECRET`, must match the WEB_CLIENT `client_secret` in `oauth2_registered_client`); `token-store.refresh-token-ttl-seconds` defaults to `3600` (override: `REFRESH_TOKEN_TTL_SECONDS`)
- `bff-server/src/main/resources/application.yml` — port defaults to `8083` (override: `SERVER_PORT`); Redis at `${REDIS_HOST:localhost}:${REDIS_PORT:6379}` (Spring Session store); CORS origin defaults to `http://localhost:3000` (override: `WEB_CLIENT_ORIGIN` via `web.client.origin` property)
- `bff-server/src/main/resources/application.yml` — port defaults to `8083` (override: `SERVER_PORT`); Redis at `${REDIS_HOST:localhost}:${REDIS_PORT:6379}` (Spring Session + token store); CORS origin defaults to `http://localhost:3000` (override: `WEB_CLIENT_ORIGIN` via `web.client.origin`); `auth-server.location` defaults to `http://localhost:9000` (override: `AUTH_SERVER_LOCATION`); `web.client.id` defaults to `WEB_CLIENT` (override: `WEB_CLIENT_ID`); `web.client.secret` is **required with no default** (`WEB_CLIENT_SECRET`, must match the WEB_CLIENT `client_secret` in `oauth2_registered_client`); `token-store.refresh-token-ttl-seconds` defaults to `3600` (override: `REFRESH_TOKEN_TTL_SECONDS`)
- `bff-server/src/main/resources/application.yml` — port defaults to `8083` (override: `SERVER_PORT`); Redis at `${REDIS_HOST:localhost}:${REDIS_PORT:6379}` (Spring Session + token store); CORS origin defaults to `http://localhost:3000` (override: `WEB_CLIENT_ORIGIN` via `web.client.origin`); `auth-server.internal-location` and `auth-server.external-location` both default to `http://localhost:9000` (overrides: `AUTH_SERVER_INTERNAL_LOCATION` / `AUTH_SERVER_EXTERNAL_LOCATION` — internal is what the RestClient calls, external is what browser-facing redirects use); `web.client.id` defaults to `WEB_CLIENT` (override: `WEB_CLIENT_ID`); `web.client.secret` is **required with no default** (`WEB_CLIENT_SECRET`, must match the WEB_CLIENT `client_secret` in `oauth2_registered_client`); `token-store.refresh-token-ttl-seconds` defaults to `3600` (override: `REFRESH_TOKEN_TTL_SECONDS`)
- `web-client/nuxt.config.ts` — `runtimeConfig.public.simpleResourceServerUrl` defaults to `http://localhost:8081` (override: `NUXT_PUBLIC_SIMPLE_RESOURCE_SERVER_URL`); `runtimeConfig.public.authServerUrl` defaults to `http://localhost:9000` (override: `NUXT_PUBLIC_AUTH_SERVER_URL`); `runtimeConfig.public.webClientId` defaults to `WEB_CLIENT` (override: `NUXT_PUBLIC_WEB_CLIENT_ID`); `runtimeConfig.public.webClientSecret` has no default and **must** be set via `NUXT_PUBLIC_WEB_CLIENT_SECRET` (must match the `client_secret` stored in auth-server's `oauth2_registered_client` table)
- All other services use `application.properties` with minimal config; most config is expected to come from `config-server`
- All services target **Java 21** and use **Spring Boot 4.0.5** with **Spring Cloud 2025.1.1**

## CI / CD — GitHub Actions

Workflows live in `.github/workflows/`. CI workflows run on `pull_request` events `opened` and `synchronize`. CD workflows run on `push` to `main` (i.e. after a PR merges).

**The auth-server, account-management, and bff-server CI workflows all use the root `docker-compose.yml`** to stand up services on the shared `roots_backend` network, then run the integration tests from the runner host (the test clients still hit `localhost:9000`/`localhost:8082`/`localhost:8083`, so the test `application.yml` files are unchanged). Each workflow names its target service in `docker compose up -d --wait <service>` so `depends_on` chains in exactly what it needs — a bare `up -d` would boot **every** compose service and pull images the suite doesn't use. The DB **self-seeds** from `auth-server/src/main/resources/initialize_db/`, mounted into the container's `/docker-entrypoint-initdb.d` (MySQL runs the scripts in filename order, which matches the dependency order). Compose references each app image as `${DOCKERHUB_USERNAME}/<service>:${<SERVICE>_TAG:-latest}` and reads `SPRING_PROFILES_ACTIVE` from the environment; each workflow builds its **subject** service into a local `:ci` image (`mvn jib:dockerBuild`) and overrides that tag, while leaving dependency services to pull `:latest`. `docker compose up -d --wait` blocks until all started containers report healthy (the curl wait-loops are gone). Each workflow ends with a `docker compose logs --no-color` step guarded by `if: failure()`.

### auth-server-ci.yml — `paths: auth-server/**`

1. Runs the frontend unit tests (`actions/setup-node` + `npm install` + `npm run test` in `auth-server/frontend`) as a fail-fast gate before any Maven build. `npm install`, not `npm ci`, because the lockfile is gitignored.
2. Builds with `mvn package -DskipTests` — builds the JAR, test classes, and the embedded Nuxt frontend once.
3. Builds the auth-server image locally with `mvn jib:dockerBuild -Djib.to.image=$DOCKERHUB_USERNAME/auth-server:ci` (loaded into the runner's Docker daemon — no registry push).
4. `docker compose up -d --wait account-management` brings up `auth-server-db`, `auth-server` (the local `:ci` image via `AUTH_SERVER_TAG=ci`), and `account-management` (pulled `:latest` — the integration suite creates its account fixtures through it), with `SPRING_PROFILES_ACTIVE=test` (so `emailSender.enabled=false` — no real emails). The DB self-seeds; `--wait` blocks until all three are healthy. Naming `account-management` scopes the up so other compose services (e.g. `bff-server`) are not booted.
5. Runs integration tests with `mvn surefire:test`.

**Required GitHub secrets:** `DOCKERHUB_USERNAME` (names the local `:ci` image — no push happens here), `MYSQL_AUTH_SERVER_ROOT_USERNAME` (set to `root`), `MYSQL_AUTH_SERVER_ROOT_PASSWORD`, real `SPRING_MAIL_USERNAME`/`SPRING_MAIL_PASSWORD` (Gmail address + App Password), and `GOOGLE_CLIENT_SECRET` — exported as `NUXT_PUBLIC_GOOGLE_CLIENT_SECRET` on the `mvn package` step (frontend **build-time** var: `nuxt generate` bakes it into the bundle; no test exercises Google login, but CI proves the project builds with the secret wired in). The mail secrets are required even though the `test` profile sends no email: the `JavaMailSender` is built in every profile and the Actuator mail health indicator opens an SMTP connection on each `/actuator/health` poll, so invalid creds would fail the `--wait` healthcheck. Inside the network, compose sets `MYSQL_AUTH_SERVER_DB_URL=jdbc:mysql://auth-server-db:3307/auth-server-db` (no longer overridden by the workflow).

**`auth-server/frontend/package-lock.json` is gitignored.** It was removed from version control to prevent platform-specific native binary mismatches (Windows-generated lockfiles don't include Linux binaries required in CI). The `frontend-maven-plugin` regenerates it on each build for the current platform.

**`WEB_CLIENT` client secret** in `create_client_table.sql` is seeded as `{noop}secret`, matching `web-client-secret` in `src/test/resources/application.yml`.

### auth-server-cd.yml — `paths: auth-server/**`

Triggers on push to `main`. Skipped automatically when the commit message contains `[skip ci]` (used by the version-bump bot commit to prevent a loop).

1. Reads the current `<version>` from `auth-server/pom.xml` (e.g. `0.0.1-SNAPSHOT`).
2. Strips `-SNAPSHOT` and increments the patch digit to produce the **release version** (e.g. `0.0.2`).
3. Sets `pom.xml` to the release version with `mvn versions:set`.
4. Builds and pushes the Docker image via `mvn jib:build -DskipTests` — base image `eclipse-temurin:21-jre`; pushes two tags: `<release-version>` and `latest` (e.g. `yourname/auth-server:0.0.2` and `yourname/auth-server:latest`). The build step exports `NUXT_PUBLIC_GOOGLE_CLIENT_SECRET` (from the `GOOGLE_CLIENT_SECRET` secret): the `compile` phase runs `nuxt generate`, which bakes the secret into the pushed image's frontend bundle — without it, Google login is broken in that image and no runtime env var can fix it.
5. Sets `pom.xml` to the next SNAPSHOT (e.g. `0.0.2-SNAPSHOT`) and commits it back to `main` as `github-actions[bot]` with `[skip ci]` in the message.

**Required GitHub secrets:** `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `GOOGLE_CLIENT_SECRET`.

**Required one-time repo setup:**
- Settings → Actions → General → Workflow permissions → **Read and write permissions**
- Settings → Branches → main protection rule → Allow specified actors to bypass required pull requests → add **GitHub Actions**

### simple-resource-server-ci.yml — `paths: simple-resource-server/**`

Runs `mvn test`, which executes `contextLoads()` in `SimpleResourceServerApplicationTests`. No external services are needed — the JWK set is fetched lazily (on the first authenticated request, not at startup), so auth-server does not need to be running.

### account-management-ci.yml — `paths: account-management/src/**`, `account-management/pom.xml`

Runs the integration tests against **both** live services, all on the shared docker network. Steps: (1) a fast unit-test gate, `mvn test '-Dtest=%regex[.*unit.*]'` (pure Mockito/MockMvc — no DB or services — fails before anything is built); (2) `docker login` (the `auth-server` image repo is private), since auth-server is an **unchanged dependency** here — the `paths` filter means a triggering PR only touched account-management — so it is **pulled** as `:latest` rather than rebuilt; (3) `mvn package -DskipTests` to build the account-management JAR + test classes; (4) `mvn jib:dockerBuild -Djib.to.image=$DOCKERHUB_USERNAME/account-management:ci` to build its image locally; (5) `docker compose up -d --wait account-management` with `ACCOUNT_MANAGEMENT_TAG=ci` and `SPRING_PROFILES_ACTIVE=test`, which `depends_on`-chains in the self-seeding DB and auth-server (pulled `:latest`) and blocks until **all three** are healthy; (6) `mvn surefire:test '-Dtest=%regex[.*integration.*]'` against `localhost:8082`/`localhost:9000` — a failing integration test fails the job; (7) `docker compose logs --no-color` on failure. auth-server runs under `SPRING_PROFILES_ACTIVE=test` (no email sent) but still builds the `JavaMailSender` and runs the Actuator mail health indicator, so real `SPRING_MAIL_*` secrets are supplied (otherwise the `--wait` health check fails). The host-run `@SpringBootTest` contextLoads smoke test was removed — the healthy account-management container is the de facto context-load proof — so no host-run test touches the DB and the DB port is not published. **Required secrets:** `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` (login + pull private `auth-server:latest`), `MYSQL_AUTH_SERVER_ROOT_USERNAME` (= `root`), `MYSQL_AUTH_SERVER_ROOT_PASSWORD`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`. Inside the network, compose sets `MYSQL_AUTH_SERVER_DB_URL=jdbc:mysql://auth-server-db:3307/auth-server-db` and `AUTH_SERVER_JWK_URI=http://auth-server:9000/oauth2/jwks` (no longer overridden by the workflow).

### account-management-cd.yml — `paths: account-management/src/**`, `account-management/pom.xml`

Triggers on push to `main`; skips its own version-bump commit via `[skip ci]`. Same pattern as auth-server-cd: read `<version>` from `pom.xml`, strip `-SNAPSHOT` and bump the patch to the release version, `mvn versions:set`, build and push the Docker image via `mvn jib:build -DskipTests` (base `eclipse-temurin:21-jre`; tags `<release-version>` and `latest`), then set the next `-SNAPSHOT` and commit it back to `main` as `github-actions[bot]`. Checkout/push use a `GH_PAT`. **Required secrets:** `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `GH_PAT`.

### bff-server-ci.yml — `paths: bff-server/src/**`, `bff-server/pom.xml`

Same shape as account-management-ci, minus a unit-test gate (bff-server has no unit tests yet): (1) `docker login` — auth-server is an unchanged dependency here, pulled `:latest`; (2) `mvn package -DskipTests` to build the JAR + test classes; (3) `mvn jib:dockerBuild -Djib.to.image=$DOCKERHUB_USERNAME/bff-server:ci`; (4) `docker compose up -d --wait bff-server` with `BFF_SERVER_TAG=ci`, `SPRING_PROFILES_ACTIVE=test`, and `WEB_CLIENT_SECRET=secret` (hardcoded in the workflow, not a GitHub secret — it matches the `{noop}secret` WEB_CLIENT seed already public in the checked-in SQL), which `depends_on`-chains in `bff-server-redis` and `auth-server` (which chains the self-seeding DB) and blocks until all are healthy — the bff-server healthcheck polls `/actuator/health`, whose Redis health indicator proves the Spring Session store is reachable; (5) `mvn surefire:test '-Dtest=%regex[.*integration.*]'` against `localhost:8083` (session smoke + the four `/api/auth/status` paths, which also drive a real guest login against the auth-server container); (6) `docker compose logs --no-color` on failure. `SPRING_MAIL_*` secrets are supplied for auth-server's mail bean + Actuator mail health check, as in the other workflows. **Required secrets:** `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `MYSQL_AUTH_SERVER_ROOT_USERNAME` (= `root`), `MYSQL_AUTH_SERVER_ROOT_PASSWORD`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`. **Ordering dependency:** the `/api/auth/status` tests assert id_token claims that only exist once the auth-server id_token enrichment has merged and its CD has pushed a new `:latest` — the auth-server changes must land first.

### bff-server-cd.yml — `paths: bff-server/src/**`, `bff-server/pom.xml`

Identical pattern to account-management-cd: version bump, `mvn jib:build` pushing `<release-version>` + `latest` to `$DOCKERHUB_USERNAME/bff-server`, next-`-SNAPSHOT` commit with `[skip ci]` via `GH_PAT`. **Required secrets:** `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `GH_PAT`. Note the first push auto-creates the `bff-server` Docker Hub repo with your account's default visibility — check it matches the other service repos.

### bff-server-ci.yml — `paths: bff-server/src/**`, `bff-server/pom.xml`

Same shape as account-management-ci, minus a unit-test gate (bff-server has no unit tests yet): (1) `docker login` — auth-server is an unchanged dependency here, pulled `:latest`; (2) `mvn package -DskipTests` to build the JAR + test classes; (3) `mvn jib:dockerBuild -Djib.to.image=$DOCKERHUB_USERNAME/bff-server:ci`; (4) `docker compose up -d --wait bff-server` with `BFF_SERVER_TAG=ci`, `SPRING_PROFILES_ACTIVE=test`, and `WEB_CLIENT_SECRET=secret` (hardcoded in the workflow, not a GitHub secret — it matches the `{noop}secret` WEB_CLIENT seed already public in the checked-in SQL), which `depends_on`-chains in `bff-server-redis` and `auth-server` (which chains the self-seeding DB) and blocks until all are healthy — the bff-server healthcheck polls `/actuator/health`, whose Redis health indicator proves the Spring Session store is reachable; (5) `mvn surefire:test '-Dtest=%regex[.*integration.*]'` against `localhost:8083` (session smoke + the four `/api/auth/status` paths, which also drive a real guest login against the auth-server container); (6) `docker compose logs --no-color` on failure. `SPRING_MAIL_*` secrets are supplied for auth-server's mail bean + Actuator mail health check, as in the other workflows. **Required secrets:** `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `MYSQL_AUTH_SERVER_ROOT_USERNAME` (= `root`), `MYSQL_AUTH_SERVER_ROOT_PASSWORD`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`. **Ordering dependency:** the `/api/auth/status` tests assert id_token claims that only exist once the auth-server id_token enrichment has merged and its CD has pushed a new `:latest` — the auth-server changes must land first.

### bff-server-cd.yml — `paths: bff-server/src/**`, `bff-server/pom.xml`

Identical pattern to account-management-cd: version bump, `mvn jib:build` pushing `<release-version>` + `latest` to `$DOCKERHUB_USERNAME/bff-server`, next-`-SNAPSHOT` commit with `[skip ci]` via `GH_PAT`. **Required secrets:** `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `GH_PAT`. Note the first push auto-creates the `bff-server` Docker Hub repo with your account's default visibility — check it matches the other service repos.

## Database

Auth-server DB schema (MySQL 8, port 3307). The `account-management` service shares this same DB and the `user_credential`/`role` tables (it has no schema of its own):
- `user_credential` — stores `email`, `name`, bcrypt `password`, `user_guid` UUID, `is_mfa_enabled` (boolean, default `true`), `is_email_verified` (boolean, default `false`), and `is_password_change_required` (boolean, default `false`; drives the forgot-password reset flow)
- `role` — many roles per credential, linked by `credential_id`; the role FK has no `ON DELETE CASCADE`, so account-management deletes role rows before the credential
- `one_time_tokens` — Spring Security `JdbcOneTimeTokenService` schema (`token_value` PK, `username`, `expires_at`); backs the account-creation magic-link token. Standalone (no FK); `username` holds the user's email. Rows are deleted on consume; expired-but-unclicked rows are not cleaned up (dev/test scale)

SQL scripts to create tables and seed test data are in `auth-server/src/main/resources/initialize_db/`.
