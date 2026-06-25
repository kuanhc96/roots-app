# roots-app

A Spring Cloud microservices application providing authentication, authorization, and role-based resource access.

## Services

| Service | Tech | Port | Role |
|---|---|---|---|
| `eureka-server` | Spring Cloud Netflix Eureka | — | Service discovery registry |
| `config-server` | Spring Cloud Config | — | Centralized configuration |
| `gateway-server` | Spring Cloud Gateway (WebFlux) | — | API gateway / routing |
| `auth-server` | Spring Boot + Nuxt/Vue | 9000 | Authentication + embedded SSR frontend |
| `bff-server` | Spring Boot | — | Backend-for-frontend |
| `simple-resource-server` | Spring Boot | 8081 | Example protected resource with role endpoints |
| `account-management` | Spring Boot | 8082 | Account CRUD resource server (integration-test-only endpoints so far) |
| `web-client` | Nuxt 4 / Vue 3 | 3000 | Standalone frontend |

**Startup order:** `auth-server` must start before `account-management` — it provides the JWK set and owns the shared DB schema/seed that account-management depends on.

## Docker Compose

`docker-compose.yml` defines the integration-test stack on a shared `roots_backend` bridge network: `auth-server-db` (MySQL 8, internal port `3307`), `auth-server` (`9000`), and `account-management` (`8082`). It is the same compose file the CI workflows use to stand up services for integration testing.

- **DB self-seeds.** `auth-server/src/main/resources/initialize_db/` is mounted into the DB container's `/docker-entrypoint-initdb.d`, so MySQL runs the schema + seed scripts automatically on first init (in dependency order, by filename) — no manual seed step.
- **App images.** `auth-server` and `account-management` reference `${DOCKERHUB_USERNAME}/<service>:${<SERVICE>_TAG:-latest}`, so by default they pull the published `:latest`. CI overrides the relevant tag to a locally-built `:ci` image.
- **Profile is env-driven.** Both app services read `SPRING_PROFILES_ACTIVE` from the environment (CI sets it to `test`).
- **Healthchecks + `depends_on`.** `account-management` waits for both `auth-server-db` and `auth-server` to be healthy; `docker compose up --wait` blocks until everything reports healthy.

Environment expected by `docker compose up`: `DOCKERHUB_USERNAME`, `MYSQL_AUTH_SERVER_ROOT_USERNAME`, `MYSQL_AUTH_SERVER_ROOT_PASSWORD`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, and `SPRING_PROFILES_ACTIVE` (plus optional `AUTH_SERVER_TAG` / `ACCOUNT_MANAGEMENT_TAG` to override the default `:latest`).

## CI / CD

GitHub Actions workflows are defined in `.github/workflows/`.

### CI — runs on pull requests

| Workflow | Trigger path | What it does |
|---|---|---|
| `auth-server-ci.yml` | `auth-server/**` | Builds a local `:ci` auth-server image (`mvn jib:dockerBuild`), brings up DB + auth-server via `docker compose up --wait` (DB self-seeds from `initialize_db/`), and runs integration tests against `localhost:9000` |
| `account-management-ci.yml` | `account-management/src/**`, `account-management/pom.xml` | Runs unit tests, builds a local `:ci` account-management image, then `docker compose up --wait` brings up DB + auth-server (pulled `:latest`) + account-management before running integration tests against `localhost:8082`/`localhost:9000` |
| `simple-resource-server-ci.yml` | `simple-resource-server/**` | Runs `mvn test` to verify the service context loads |

### CD — runs on push to `main`

| Workflow | Trigger path | What it does |
|---|---|---|
| `auth-server-cd.yml` | `auth-server/**` | Strips `-SNAPSHOT`, increments the patch version, builds and pushes a Docker image via Jib, then commits the next SNAPSHOT version back to `main` |

#### auth-server CD details

1. Reads the current version from `auth-server/pom.xml` (e.g. `0.0.1-SNAPSHOT`).
2. Produces the release version by stripping `-SNAPSHOT` and incrementing the patch digit (e.g. `0.0.2`).
3. Builds and pushes the Docker image with `mvn jib:build -DskipTests` — base image `eclipse-temurin:21-jre`; pushes two tags: `<release-version>` and `latest`.
4. Commits `pom.xml` back to `main` at the next SNAPSHOT (e.g. `0.0.2-SNAPSHOT`) with `[skip ci]` to prevent a loop.

**Required secrets:**

| Secret | Purpose |
|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub account name used to tag and push the image |
| `DOCKERHUB_TOKEN` | Docker Hub access token (not your account password) used to authenticate the `jib:build` push |
| `GH_PAT` | GitHub Personal Access Token with `contents: write` permission; used to push the version-bump commit back to `main`, bypassing branch protection rules that block the default `GITHUB_TOKEN` from pushing to protected branches |

**Required one-time repo setup:**
- Settings → Actions → General → Workflow permissions → **Read and write permissions**
- Settings → Branches → main protection rule → Allow specified actors to bypass required pull requests → add **GitHub Actions**
