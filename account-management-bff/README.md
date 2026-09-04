# account-management-bff

A Spring Boot backend-for-frontend for the account-management client. It follows the same browser-session pattern as `bff-server`, but is dedicated to the account-management UI and uses a separate Redis-backed session cookie named `__Host-AMC_SESSION`.

Its job is to own the browser OAuth2 flow for the account-management client without exposing tokens in the browser. The browser holds only the session cookie; the access token, refresh token, and id token live in Redis keyed by the session id.

## What it does

`account-management-bff` exposes a small auth surface under `/api/auth`:

- `GET /api/auth/status` — returns whether the browser is logged in
- `GET /api/auth/authorize` — begins the Authorization Code + PKCE flow against auth-server
- `GET /api/auth/callback` — validates state / PKCE, exchanges the auth code, stores the token set, and redirects back to the account-management client
- `GET /api/auth/logout` — clears the stored tokens, invalidates the session, and redirects to auth-server's OIDC logout flow

This mirrors the BFF pattern used by the main `web-client`, but with a dedicated confidential PKCE client:

- `ACCOUNT_MANAGEMENT_CLIENT` — standard account-management client
- `ACCOUNT_MANAGEMENT_SUPER_CLIENT` — privileged variant for the broader account-management feature set

The service is built for the account-management client at port `8084` and uses the shared `bff-server-redis` instance on Redis port `6379`.

## Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `SERVER_PORT` | No | `8084` | Port used by the BFF |
| `REDIS_HOST` | No | `localhost` | Redis host for the session and token store |
| `REDIS_PORT` | No | `6379` | Redis port |
| `ACCOUNT_MANAGEMENT_BFF_EXTERNAL_LOCATION` | No | `http://localhost:8084` | Base URL used in redirects the browser follows |
| `AUTH_SERVER_INTERNAL_LOCATION` | No | `http://localhost:9000` | Auth-server URL used by the server-to-server token exchange |
| `AUTH_SERVER_EXTERNAL_LOCATION` | No | `http://localhost:9000` | Auth-server URL used in browser redirects |
| `ACCOUNT_MANAGEMENT_CLIENT_ID` | No | `ACCOUNT_MANAGEMENT_CLIENT` | Client ID used for the auth-code flow |
| `ACCOUNT_MANAGEMENT_CLIENT_SECRET` | Yes | — | Secret for the confidential PKCE client; must match the seeded `{noop}secret` in auth-server's DB |
| `ACCOUNT_MANAGEMENT_CLIENT_ORIGIN` | No | `http://localhost:3001` | Allowed client origin; used for the post-login redirect |
| `EUREKA_SERVER_URL` | No | `http://localhost:8070/eureka/` | Eureka registry URL |
| `REFRESH_TOKEN_TTL_SECONDS` | No | `3600` | Redis TTL for stored refresh tokens |

## Session and Redis model

The service uses Spring Session with a secure, HttpOnly cookie:

- cookie name: `__Host-AMC_SESSION`
- secure: `true`
- httpOnly: `true`
- sameSite: `Lax`
- path: `/`

Redis stores token entries keyed by the session id, following the same pattern as the other BFFs:

- `<sessionId>:access_token`
- `<sessionId>:refresh_token`
- `<sessionId>:id_token`
- `<sessionId>:oauth_state`
- `<sessionId>:oauth_code_verifier`
- `<sessionId>:oauth_nonce`

The service stores expiry TTLs alongside each token, and a missing key is treated as an expired/invalid token state.

## Authorization flow

The flow matches the account-management BFF's architecture:

1. Browser calls `GET /api/auth/authorize`.
2. `AuthorizeService` generates:
   - a random `state`
   - a PKCE `code_verifier`
   - a `code_challenge` (SHA-256, base64url)
   - a nonce
3. Those values are stored in Redis under the session id with a 5-minute TTL.
4. The BFF redirects the browser to:
   - `${auth-server.external-location}/oauth2/authorize`
   - with `response_type=code`
   - `client_id=ACCOUNT_MANAGEMENT_CLIENT`
   - `redirect_uri=${account-management-bff.external-location}/api/auth/callback`
   - `scope=openid ACCOUNT_MANAGEMENT_CLIENT`
   - `state`, `nonce`, `code_challenge`, and `code_challenge_method=S256`
5. `GET /api/auth/callback` validates the `state` and PKCE challenge, exchanges the authorization code with auth-server, verifies the returned `id_token` nonce, and stores the full token set in Redis.
6. The browser is redirected to the account-management client home page (`ACCOUNT_MANAGEMENT_CLIENT_ORIGIN`).

If any validation fails, the browser is redirected to `/?e=login_failed` on the client origin.

## Status and refresh behavior

`GET /api/auth/status` is always 200, with "not logged in" returned as a normal outcome.

The logic is:

1. If an `id_token` exists for the session, decode it and return the user claims (`email`, `userGUID`, `roles`).
2. If no `id_token` is present but a `refresh_token` exists, use the refresh-token grant against auth-server and store the refreshed token set.
3. If the refresh fails, delete the stored refresh token and return `notLoggedIn`.
4. If neither token exists, return `notLoggedIn`.

That means the account-management client can be treated as a session-backed app without putting tokens in the browser.

## Logout flow

`GET /api/auth/logout` does the server-side logout sequence:

1. Reads the session's tokens
2. Clears the Redis token keys
3. Invalidates the HTTP session
4. Redirects the browser to auth-server's `/connect/logout` with the client metadata so the upstream OIDC logout completes

This is a server-side RP-initiated logout, matching the BFF pattern used elsewhere in the repo.

## Running locally

```bash
cd account-management-bff
ACCOUNT_MANAGEMENT_CLIENT_SECRET=secret mvn spring-boot:run
```

You will also need:

- a healthy `eureka-server`
- a healthy `auth-server`
- the shared `bff-server-redis` (port `6379`)

## Docker Compose

The service is included in the root Compose file and is exposed at port `8084`:

```bash
docker compose up -d --wait account-management-bff
```

The compose stack sets:

- `REDIS_HOST=bff-server-redis`
- `AUTH_SERVER_INTERNAL_LOCATION=http://auth-server:9000`
- `ACCOUNT_MANAGEMENT_CLIENT_SECRET=${ACCOUNT_MANAGEMENT_CLIENT_SECRET:-secret}`
- `EUREKA_SERVER_URL=http://eureka-server:8070/eureka/`

## CI

The workflow at `.github/workflows/account-management-bff-ci.yml` does the following:

1. logs in to Docker Hub
2. builds the `account-management-bff` jar and local image via Jib
3. starts Redis + auth-server + the service on the shared Docker network
4. waits for `http://localhost:8084/actuator/health` to report `UP`
5. dumps container logs on failure

This is a narrow PR-only CI gate for the account-management BFF.

## Related services

- `auth-server` — issues the OAuth2 tokens and validates the confidential client
- `account-management` — protected resource server that enforces access rules
- `bff-server` — the main web-client token broker, analogous in structure but distinct in cookie/session and client identity
- `gateway-server` — front-door routing and token attachment for the broader platform
