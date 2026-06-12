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

CI runs the [integration tests](#integration-tests) and fails the job if any of them fail. Because those tests need a live `auth-server` (for the `client_credentials` token and the JWK set) **and** a running account-management, the workflow boots both services against a shared MySQL before running the tests.

### What it does

1. Starts a MySQL 8 service container (port 3306) with an `auth-server-db` database.
2. **Seeds the shared schema** by running the `auth-server` scripts in `auth-server/src/main/resources/initialize_db/` (`create_authentication_tables.sql` → `create_client_table.sql` → `create_one_time_tokens_table.sql` → `initialize_test_users.sql`). This creates the `user_credential`/`role` tables account-management writes to and seeds the `INTEGRATION_TEST_CLIENT` that issues the `client_credentials` token.
3. Builds and starts **auth-server** (`mvn package -DskipTests` → `java -jar`), then polls `GET http://localhost:9000/actuator/health` until `UP` (up to 150 s).
4. Builds and starts **account-management** (`mvn package -DskipTests` — this also compiles the test sources → `java -jar`), then polls `GET http://localhost:8082/actuator/health` until `UP` (up to 150 s).
5. Runs the integration tests with `mvn surefire:test`. A failure fails the job.

### Required GitHub secrets

| Secret | Value in CI |
|---|---|
| `MYSQL_AUTH_SERVER_ROOT_USERNAME` | `root` (the MySQL service container only creates a root user) |
| `MYSQL_AUTH_SERVER_ROOT_PASSWORD` | any password |
| `SPRING_MAIL_USERNAME` | Gmail username — required because auth-server is started in this job and validates the mail config at startup |
| `SPRING_MAIL_PASSWORD` | Gmail App Password (same reason) |

`MYSQL_AUTH_SERVER_DB_URL` is hardcoded in the workflow to `jdbc:mysql://localhost:3306/auth-server-db` (the GitHub Actions MySQL container exposes port 3306, not 3307). `AUTH_SERVER_JWK_URI` is left at its default (`http://localhost:9000/oauth2/jwks`) — auth-server is running on that port, so account-management can fetch the JWK set to validate the bearer token.

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
