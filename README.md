# MiniCRM

Customer 360 + Opportunity Management — a Java learning project. See
[`spec/design-spec.md`](spec/design-spec.md) for the full design and
[`JAVA_REVIEW.md`](JAVA_REVIEW.md) for the topic-by-topic curriculum checklist.
Each milestone's runnable evidence is under [`docs/learning/`](docs/learning/).

## Requirements

- Java 25, or Docker (this repo's `scripts/*.sh` fall back to
  `maven:3.9-eclipse-temurin-25` automatically when no local JDK is found)

## Build and test

Start the existing local PostgreSQL container before running JDBC tests:

```sh
docker start minicrm-postgres
```

`PostgresConnectionConfig.local()` uses these defaults:

| Environment override | Default |
|---|---|
| `CRM_DB_URL` | `jdbc:postgresql://localhost:5432/minicrm` |
| `CRM_DB_USERNAME` | `postgres` |
| `CRM_DB_PASSWORD` | `test` |

These credentials are for local development. Data persists in Docker volume
`minicrm-postgres-data`. JDBC tests use the same database, migrate a separate
`minicrm_test` schema, and clear only its test tables. Run one JDBC test suite at
a time against this shared schema. Tests do not start or remove containers.

On a fresh machine, create the container once:

```sh
docker run -d --name minicrm-postgres \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=test -e POSTGRES_DB=minicrm \
  -p 127.0.0.1:5432:5432 \
  -v minicrm-postgres-data:/var/lib/postgresql/data postgres:16.4
```

```sh
./scripts/ci.sh
```

Equivalent to `./mvnw -B verify`.

## Modules

| Module | Purpose |
|---|---|
| `crm-domain` | Domain model only — no Spring, no JDBC (enforced by ArchUnit) |

More modules (`crm-application`, `crm-persistence`, `crm-databricks`, `crm-mcp`,
`crm-api`, `crm-loadtest`) are added milestone by milestone per design-spec §4/§13.
