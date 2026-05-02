# simple-resource-server

> **Note:** This service is intended for testing and development purposes only. It will be deprecated in a future release once a production resource server is in place.

## Overview

`simple-resource-server` is an OAuth2 Resource Server that demonstrates role-based access control using JWT tokens issued by `auth-server`. It exposes a set of role-specific endpoints under `/api/role/` that require a valid bearer token and the appropriate role to access.

It is part of the broader roots-app microservices system and depends on `auth-server` for token issuance and JWK verification.

## Relationship to auth-server

`auth-server` acts as the OAuth2 Authorization Server. It issues signed JWTs to clients after they complete the Authorization Code flow. `simple-resource-server` validates those JWTs by fetching the public JWK set from `auth-server` at startup and on key rotation.

A valid token must contain:
- A `scope` claim including `WEB_CLIENT_READ` (mapped to the `WEB_CLIENT_READ` authority)
- A `roles` claim listing the user's roles in uppercase (e.g. `PASTOR`, `MEMBER`), mapped to `ROLE_*` authorities

The `roles` claim must be added to JWTs via an `OAuth2TokenCustomizer` on the auth-server side.

## Endpoints

All endpoints return `text/plain`. Requests without a valid JWT are rejected.

| Endpoint | Required scope | Required role | Response |
|---|---|---|---|
| `GET /api/role/pastor` | `WEB_CLIENT_READ` | `ROLE_PASTOR` | `I am a pastor` |
| `GET /api/role/deacon` | `WEB_CLIENT_READ` | `ROLE_DEACON` | `I am a deacon` |
| `GET /api/role/small-group-leader` | `WEB_CLIENT_READ` | `ROLE_SMALL_GROUP_LEADER` | `I am a small group leader` |
| `GET /api/role/vice-small-group-leader` | `WEB_CLIENT_READ` | `ROLE_VICE_SMALL_GROUP_LEADER` | `I am a vice small group leader` |
| `GET /api/role/member` | `WEB_CLIENT_READ` | `ROLE_MEMBER` | `I am a member` |
| `GET /api/role/guest` | _(none — public)_ | _(none)_ | `I am a guest` |

CORS is allowed from `http://localhost:3000` by default (see `WEB_CLIENT_ORIGIN` below).

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8081` | Port the service listens on |
| `AUTH_SERVER_JWK_URI` | `http://localhost:9000/oauth2/jwks` | JWK set URI used to verify JWT signatures |
| `WEB_CLIENT_ORIGIN` | `http://localhost:3000` | Origin allowed by CORS on all `/api/role/` endpoints |

## Running

```bash
cd simple-resource-server
mvn spring-boot:run
```

`auth-server` must be running and reachable at `AUTH_SERVER_JWK_URI` before or shortly after startup, as Spring Security fetches the JWK set to validate incoming tokens.
