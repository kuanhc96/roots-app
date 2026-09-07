# Account Management Client

A Nuxt 4 / Vue 3 client for managing user accounts. Authenticates via **account-management-bff**, which handles OAuth2 tokens server-side in Redis.

## Development

### Prerequisites

- Node.js 18+
- npm

### Installation

```bash
npm install
```

### Running the dev server

```bash
npm run dev
```

The client will start on **http://localhost:3001** by default.

### Features

- **Check Status** button — polls account-management-bff to check login status
- **Authorize** button — kicks off the OAuth2 authorization code flow
- **Login** button — checks status and authorizes if not logged in
- **Logout** button — server-side logout with RP-Initiated OIDC logout

When logged in, the UI displays the user's email and roles.

### Configuration

The BFF endpoint is configured in `nuxt.config.ts` under `runtimeConfig.public.accountManagementBffUrl`.

Override via the `NUXT_PUBLIC_ACCOUNT_MANAGEMENT_BFF_URL` environment variable:

```bash
NUXT_PUBLIC_ACCOUNT_MANAGEMENT_BFF_URL=http://localhost:8080/roots-app/account-management-bff npm run dev
```

### Building for production

```bash
npm run build
npm run preview
```

### Static generation

```bash
npm run generate
```
