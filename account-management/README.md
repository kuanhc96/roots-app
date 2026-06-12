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

Connection targets are configured in `src/test/resources/application.yml` (`auth-server-location`, `account-management-location`, `integration-test-client-secret`); override on the command line with `-D<property>=<value>`. These tests are **not** run by CI (see below).

## CI

The workflow at `.github/workflows/account-management-ci.yml` runs on pull requests that touch `account-management/src/**` or `account-management/pom.xml` (events: `opened`, `synchronize`).

CI simply verifies the service **starts successfully** before a merge. The [integration tests](#integration-tests) are **not** run in CI: they require a live `auth-server` (for the `client_credentials` token and JWK set) plus a running account-management, neither of which the CI job provides.

### What it does

1. Starts a MySQL 8 service container with an empty `auth-server-db` database. No seeding is required: the actuator `db` health indicator only needs a reachable datasource, and the account endpoints (which read/write `user_credential`/`role`) are never hit by the startup check.
2. Builds the JAR with `mvn package -DskipTests`.
3. Starts the service in the background with `java -jar`.
4. Polls `GET /actuator/health` until the service reports `UP` (up to 150 s). If it never comes up, the job fails.

### Required GitHub secrets

| Secret | Value in CI |
|---|---|
| `MYSQL_AUTH_SERVER_ROOT_USERNAME` | `root` (the MySQL service container only creates a root user) |
| `MYSQL_AUTH_SERVER_ROOT_PASSWORD` | any password |

`MYSQL_AUTH_SERVER_DB_URL` is hardcoded in the workflow to `jdbc:mysql://localhost:3306/auth-server-db` (the GitHub Actions MySQL container exposes port 3306, not 3307). `AUTH_SERVER_JWK_URI` is left at its default — the JWK set is fetched lazily, so `auth-server` need not be running for the startup check to pass.

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
