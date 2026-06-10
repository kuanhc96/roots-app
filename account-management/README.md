# account-management

Spring Boot service in the **roots-app** stack. Runs as an **OAuth2 Resource Server** (Spring Security 7.x) — it validates JWT bearer tokens issued by `auth-server` and enforces access via `@PreAuthorize` (`@EnableMethodSecurity`), the same way as `simple-resource-server`.

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
