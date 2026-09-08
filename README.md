# MiniCRM

Customer 360 + Opportunity Management — a Java learning project. See
[`spec/design-spec.md`](spec/design-spec.md) for the full design and
[`JAVA_REVIEW.md`](JAVA_REVIEW.md) for the topic-by-topic curriculum checklist.
Each milestone's runnable evidence is under [`docs/learning/`](docs/learning/).

## Requirements

- Java 25, or Docker (this repo's `scripts/*.sh` fall back to
  `maven:3.9-eclipse-temurin-25` automatically when no local JDK is found)

## Build and test

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
