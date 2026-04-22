# auth-server

A fullstack Spring Boot + Nuxt/Vue application that handles authentication for the roots-app platform. It embeds the Nuxt frontend as a static SPA served by Spring Boot, and manages user credentials and roles in a MySQL database.

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `MYSQL_AUTH_SERVER_ROOT_USERNAME` | Yes | — | MySQL username |
| `MYSQL_AUTH_SERVER_ROOT_PASSWORD` | Yes | — | MySQL password |
| `DB_URL` | No | `jdbc:mysql://localhost:3307/auth-server-db` | JDBC connection URL |
| `SERVER_PORT` | No | `9000` | HTTP port the server listens on |

`MYSQL_AUTH_SERVER_ROOT_USERNAME` and `MYSQL_AUTH_SERVER_ROOT_PASSWORD` must be provided as JVM arguments (or environment variables) at startup, for example:

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DMYSQL_AUTH_SERVER_ROOT_USERNAME=root -DMYSQL_AUTH_SERVER_ROOT_PASSWORD=secret"
```

## Running

```bash
# Full build (includes Nuxt frontend generation)
mvn package

# Run the service
mvn spring-boot:run

# Run tests only
mvn test
```

## Database

Connects to a MySQL 8 instance on port **3307** by default. The schema is defined in `src/main/resources/initialize_db/`. Hibernate is set to `validate` mode — it checks that the schema matches the JPA entities on startup but makes no changes to the database.
