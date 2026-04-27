# web-client

Standalone Nuxt 4 / Vue 3 frontend for roots-app.

## Environment Variables

All public runtime config variables follow Nuxt's `NUXT_PUBLIC_*` naming convention and can be set as environment variables before starting the app.

| Environment Variable | Default | Description |
|---|---|---|
| `NUXT_PUBLIC_SIMPLE_RESOURCE_SERVER_URL` | `http://localhost:8081` | Base URL of `simple-resource-server` |
| `NUXT_PUBLIC_AUTH_SERVER_URL` | `http://localhost:9000` | Base URL of `auth-server`; used to redirect unauthenticated users to the login page |

## Commands

```bash
npm install         # install dependencies
npm run dev         # dev server on :3000
npm run build       # SSR build
npm run generate    # static export
npm run preview     # preview production build locally
```
