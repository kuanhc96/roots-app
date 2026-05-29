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
| `web-client` | Nuxt 4 / Vue 3 | 3000 | Standalone frontend |

**Startup order:** config-server → eureka-server → gateway-server → auth-server → bff-server → simple-resource-server → web-client.

## CI / CD

GitHub Actions workflows are defined in `.github/workflows/`.

### CI — runs on pull requests

| Workflow | Trigger path | What it does |
|---|---|---|
| `auth-server-ci.yml` | `auth-server/**` | Starts a MySQL service container, seeds the DB, builds the JAR, starts auth-server, and runs integration tests |
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
