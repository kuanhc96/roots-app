# web-client

Standalone Nuxt 4 / Vue 3 frontend for roots-app.

## Environment Variables

All public runtime config variables follow Nuxt's `NUXT_PUBLIC_*` naming convention and can be set as environment variables before starting the app.

| Environment Variable | Default | Description |
|---|---|---|
| `NUXT_PUBLIC_SIMPLE_RESOURCE_SERVER_URL` | `http://localhost:8081` | Base URL of `simple-resource-server` |
| `NUXT_PUBLIC_AUTH_SERVER_URL` | `http://localhost:9000` | Base URL of `auth-server` |
| `NUXT_PUBLIC_WEB_CLIENT_ID` | `WEB_CLIENT` | OAuth2 client ID registered on auth-server |
| `NUXT_PUBLIC_WEB_CLIENT_SECRET` | _(none — required)_ | OAuth2 client secret; must match `WEB_CLIENT_SECRET` on auth-server. Set in `.env.local` for local dev (never commit this file) |

For local development, create a `.env.local` file in this directory:

```
NUXT_PUBLIC_WEB_CLIENT_SECRET=your-secret-here
```

## OAuth2 Flow

The app uses the Authorization Code grant type. Unauthenticated users are redirected from `/home` to the auth-server's `/oauth2/authorize` endpoint. After login, the auth-server redirects to `/callback` with an authorization code. The callback page exchanges the code for an access token via `POST /oauth2/token` and stores it in `sessionStorage`. All subsequent API calls to `simple-resource-server` include the token as a `Bearer` header.

## Commands

```bash
npm install         # install dependencies
npm run dev         # dev server on :3000
npm run build       # SSR build
npm run generate    # static export
npm run preview     # preview production build locally
```
