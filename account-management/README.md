# account-management

Spring Boot service in the **roots-app** stack. Runs as an **OAuth2 Resource Server** (Spring Security 7.x) — it validates JWT bearer tokens issued by `auth-server` and enforces access via `@PreAuthorize` (`@EnableMethodSecurity`), the same way as `simple-resource-server`.

It reads and writes the **shared auth-server database** (`user_credential` and `role` tables) directly via `JdbcTemplate`; it owns no schema of its own. It exposes integration-test-only account create/delete endpoints (see [Endpoints](#endpoints)), callable only by the `INTEGRATION_TEST_CLIENT` machine client.

## Endpoints

Both endpoints live under `/api/account` (`controller/AccountController`) and are **integration-test-only**: each is guarded by `@PreAuthorize` and callable only with an `INTEGRATION_TEST_CLIENT` `client_credentials` access token obtained from `auth-server`. They let tests create and tear down accounts directly in the shared DB without driving the full signup/email-verification flow.

| Endpoint | Required authority | Behaviour |
|---|---|---|
| `POST /api/account/test` | `INTEGRATION_TEST_CLIENT_WRITE` | Creates an account from `CreateAccountRequest(name, email, password, mfaEnabled?, emailVerified?, roles?)`. `mfaEnabled` defaults to `true`, `emailVerified` to `false`; the `MEMBER` role is always added plus any requested roles (de-duplicated). Password is bcrypt-hashed; a `user_guid` UUID is generated. Returns **201** `CreateAccountResponse(name, email, userGUID, mfaEnabled, emailVerified, roles)`. |
| `DELETE /api/account/test?email=…` *or* `?userGUID=…` | `INTEGRATION_TEST_CLIENT_DELETE` | Deletes by **exactly one** of `email`/`userGUID`. Returns **204**. Idempotent — no match is a no-op, so teardown can run repeatedly. |

**Validation** (`validator/Validator`, server-side): name required and ≤ 255 chars; email required and contains `@`; password required, ≥ 8 chars with at least one uppercase, lowercase, and digit. Failures throw `InvalidRequestException` → **400**. A duplicate email throws `EmailAlreadyExistsException` → **409**. The delete endpoint requires exactly one of email/userGUID (not both, not neither) → **400**. `GlobalExceptionHandler` (`@RestControllerAdvice`) maps these to `{"error": "<message>"}`.

**Roles** (`enums/Role`): `pastor`, `deacon`, `small_group_leader`, `vice_small_group_leader`, `member`, `guest` (serialized as the lowercase value, case-insensitive on input).

**Swagger UI** is available via `springdoc-openapi-starter-webmvc-ui` (the endpoints carry `@Operation`/`@Parameter` annotations).

## Environment Variables

All variables have defaults suitable for local development; override them per environment as needed.

| Variable | Property | Default | Purpose |
|---|---|---|---|
| `SERVER_PORT` | `server.port` | `8082` | HTTP port the service listens on. |
| `AUTH_SERVER_JWK_URI` | `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | `http://localhost:9000/oauth2/jwks` | JWK Set endpoint on `auth-server` used to fetch the public keys for validating incoming JWTs. The key set is fetched lazily on the first authenticated request, so `auth-server` does not need to be running at startup. |
| `MYSQL_AUTH_SERVER_DB_URL` | `spring.datasource.url` | `jdbc:mysql://localhost:3307/auth-server-db` | JDBC URL of the shared **auth-server** MySQL database (port `3307`). Points at the same DB `auth-server` uses. |
| `MYSQL_AUTH_SERVER_ROOT_USERNAME` | `spring.datasource.username` | _(required, no default)_ | Username for the shared auth-server database. |
| `MYSQL_AUTH_SERVER_ROOT_PASSWORD` | `spring.datasource.password` | _(required, no default)_ | Password for the shared auth-server database. |

The service shares the same MySQL instance and credentials as `auth-server` (see the root `docker-compose.yml` / `CLAUDE.md`). Start it with `docker compose up -d auth-server-db`.

## Running

```bash
mvn spring-boot:run        # run the service
mvn package                # compile + test + jar
mvn test                   # run tests
```

### Integration tests

`AccountLifecycleIntegrationTest` (under `src/test/java/.../integration/`) exercises the create→delete lifecycle against **live** services: it obtains an `INTEGRATION_TEST_CLIENT` `client_credentials` token from `auth-server`, then drives `POST`/`DELETE /api/account/test` against a running account-management. Start MySQL, auth-server, and account-management first, then:

```bash
mvn test -Dtest="AccountLifecycleIntegrationTest"
```

Connection targets are configured in `src/test/resources/application.yml` (`auth-server-location`, `account-management-location`, `integration-test-client-secret`); override on the command line with `-D<property>=<value>`. These tests **are** run by CI (see below) — they are the whole point of the workflow.

## CI

The workflow at `.github/workflows/account-management-ci.yml` runs on pull requests that touch `account-management/src/**` or `account-management/pom.xml` (events: `opened`, `synchronize`).

CI runs the [integration tests](#integration-tests) and fails the job if any of them fail. Because those tests need a live `auth-server` (for the `client_credentials` token and the JWK set) **and** a running account-management, the workflow boots both services against a shared MySQL before running the tests.

### What it does

1. **Runs the unit tests first as a fast gate** with `mvn test '-Dtest=%regex[.*unit.*]'`. The unit tests under `com.roots.account_management.unit.*` are pure Mockito / standalone MockMvc — no DB or running services required — so they run before anything is built or booted. If they fail, the job fails immediately and no images are built.
2. Logs in to Docker Hub (`docker/login-action@v3`). The `account-management/**` paths filter means a triggering PR only touched account-management, so auth-server is an unchanged dependency — the workflow **pulls** the published `auth-server:latest` image (its repo is private, hence the login) rather than rebuilding it.
3. Builds the account-management JAR + test classes with `mvn package -DskipTests` (the integration tests run later via `surefire:test`).
4. Builds the account-management Docker image locally via Jib: `mvn jib:dockerBuild -Djib.to.image=${DOCKERHUB_USERNAME}/account-management:ci`. The image is loaded straight into the local Docker daemon — no registry push.
5. Brings up DB + auth-server + account-management on the shared `roots_backend` docker network with `docker compose up -d --wait account-management` (env: `ACCOUNT_MANAGEMENT_TAG=ci`, `SPRING_PROFILES_ACTIVE=test`, `MYSQL_AUTH_SERVER_ROOT_USERNAME`/`MYSQL_AUTH_SERVER_ROOT_PASSWORD`, `SPRING_MAIL_USERNAME`/`SPRING_MAIL_PASSWORD`). The DB self-seeds from `auth-server/src/main/resources/initialize_db/` (mounted into `/docker-entrypoint-initdb.d` by `docker-compose.yml`); MySQL runs each `.sql` there in alphabetical order, which already matches the dependency order (`create_authentication_tables` → `create_client_table` → `create_one_time_tokens_table` → `initialize_test_users`). `--wait` blocks until **all three** services report healthy, so no curl wait-loop is needed. auth-server is started under `SPRING_PROFILES_ACTIVE=test` (no real emails sent), but it still builds the `JavaMailSender` and runs the Actuator mail health indicator, hence the `SPRING_MAIL_*` secrets.
6. Runs the integration tests on the host (the client hits the published `localhost:8082` and `localhost:9000` against the running containers) with `mvn surefire:test '-Dtest=%regex[.*integration.*]'`. A failure fails the job.
7. On failure, dumps all container logs (`docker compose logs --no-color`).

### Required GitHub secrets

| Secret | Value in CI |
|---|---|
| `DOCKERHUB_USERNAME` | Your Docker Hub username — used both for `docker/login-action` (pulling private `auth-server:latest`) and as the Jib image prefix (`${DOCKERHUB_USERNAME}/account-management:ci`) |
| `DOCKERHUB_TOKEN` | Docker Hub access token — used to log in and pull `auth-server:latest` |
| `MYSQL_AUTH_SERVER_ROOT_USERNAME` | `root` (the MySQL container only creates a root user) |
| `MYSQL_AUTH_SERVER_ROOT_PASSWORD` | any password |
| `SPRING_MAIL_USERNAME` | Gmail username — required because auth-server is started in this job and the Actuator mail health indicator opens an SMTP connection on every `/actuator/health` poll |
| `SPRING_MAIL_PASSWORD` | Gmail App Password for the above account |

The DB is reached over the shared docker network at `auth-server-db:3307`; `MYSQL_AUTH_SERVER_DB_URL` and `AUTH_SERVER_JWK_URI` are set inside `docker-compose.yml` (`http://auth-server:9000/oauth2/jwks` between containers) and are no longer overridden by the workflow.

## CD

The workflow at `.github/workflows/account-management-cd.yml` triggers on every push to `main` that touches `account-management/src/**` or `account-management/pom.xml` (i.e. after a PR merges). Commits whose message contains `[skip ci]` are ignored — this prevents the workflow's own version-bump commit from triggering another run.

### What it does

1. Reads the current `<version>` from `pom.xml` (e.g. `0.0.1-SNAPSHOT`).
2. Strips `-SNAPSHOT` and increments the patch digit to produce the **release version** (e.g. `0.0.2`).
3. Sets `pom.xml` to the release version with `mvn versions:set`.
4. Builds and pushes the Docker image via Jib (`mvn jib:build -DskipTests`) using `eclipse-temurin:21-jre` as the base image. Two tags are pushed: the release version (e.g. `yourname/account-management:0.0.2`) and `latest`.
5. Sets `pom.xml` to the next SNAPSHOT (e.g. `0.0.2-SNAPSHOT`) and commits it back to `main` as `github-actions[bot]` with `[skip ci]` in the commit message.

### Required GitHub secrets

| Secret | Description |
|---|---|
| `DOCKERHUB_USERNAME` | Your Docker Hub username |
| `DOCKERHUB_TOKEN` | Docker Hub access token (Account Settings → Security → Access Tokens) |
| `GH_PAT` | Personal access token used to check out and push the version-bump commit back to `main` |

### Required one-time repo setup

1. **Workflow write permissions:** Settings → Actions → General → Workflow permissions → select **Read and write permissions**.
2. **Branch protection bypass:** Settings → Branches → main protection rule → Allow specified actors to bypass required pull requests → add **GitHub Actions**. This lets the bot commit the version bump directly to `main`.
