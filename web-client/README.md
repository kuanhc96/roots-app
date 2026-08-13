# web-client

Standalone Nuxt 4 / Vue 3 frontend for roots-app.

## Environment Variables

All public runtime config variables follow Nuxt's `NUXT_PUBLIC_*` naming convention and can be set as environment variables before starting the app.

| Environment Variable | Default | Description |
|---|---|---|
| `NUXT_PUBLIC_SIMPLE_RESOURCE_SERVER_URL` | `http://localhost:8080/simple-resource-server` | Gateway prefix used for simple-resource-server calls |
| `NUXT_PUBLIC_BFF_SERVER_URL` | `http://localhost:8080/bff-server` | Gateway entry point for the bff-server; the gateway proxies the `/bff-server/**` prefix to the bff-server (Eureka discovery locator strips the prefix before forwarding) |

## OAuth2 Flow

Auth is fully managed server-side by the bff-server, so the browser never sees tokens or the client secret. The web-client only ever handles id-token *claims* returned by `GET /api/auth/status`.

1. **Authorize** — `useOAuth.authorize()` navigates the browser to `{bffServerUrl}/api/auth/authorize`. The bff mints a `state`, stores it in Redis, and 302s to auth-server's `/oauth2/authorize`.
2. **Callback** — after login, auth-server redirects back to `/callback` with a `code` + `state`. The callback page exchanges the code at the bff's `/api/auth/callback`, which stores all three tokens (access, refresh, id) in Redis keyed by session id.
3. **Status** — `useOAuth.checkStatus()` fetches `{bffServerUrl}/api/auth/status` (session cookie rides along). The bff decodes the stored id-token and returns its claims; the web-client stores them in `sessionStorage`.
4. **Logout** — `useOAuth.startLogout()` navigates to `{bffServerUrl}/api/auth/logout`. The bff deletes the Redis token keys and drives OIDC RP-initiated logout against auth-server, which finally redirects the browser to `/logout`.

All auth and resource-server traffic is routed through `gateway-server` (port `8080`), including the `/simple-resource-server/**` path used by role API calls.

## Commands

```bash
npm install         # install dependencies
npm run dev         # dev server on :3000
npm run build       # SSR build
npm run generate    # static export
npm run preview     # preview production build locally
```
