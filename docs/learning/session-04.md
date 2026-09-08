# Session 04 — JDBC persistence and the local PostgreSQL baseline

## Status

Reviewed on 2026-09-08. Local PostgreSQL verification passes, but **M4 is partially
complete**. The JDBC adapters and shared migration exist. The review below identifies
local correctness and test-coverage work still needed. Lakebase authentication,
connection integration, preflight, and setup documentation are **pending and unverified**;
no remote connection was attempted during this review. This is the explicit pending
status required by design-spec §13, not a claim that local tests prove Lakebase support.

## Concept

Replace the in-memory storage mechanism with real PostgreSQL while keeping the same
application repository ports. Application services still depend on interfaces;
`crm-persistence` supplies JDBC implementations for customers, contacts, opportunities,
and activities. A `DataSource` supplies connections, so repository code does not need
to know where PostgreSQL is hosted.

Read these files in order:

1. [`PostgresConnectionConfig`](../../crm-persistence/src/main/java/com/minicrm/persistence/jdbc/PostgresConnectionConfig.java)
   and [`PostgresDataSourceFactory`](../../crm-persistence/src/main/java/com/minicrm/persistence/jdbc/PostgresDataSourceFactory.java):
   connection settings and a HikariCP pool capped at 10 connections, with a 5-second
   connection-acquisition timeout.
2. [`V1__init.sql`](../../infra/databricks/sql/postgres/V1__init.sql)
   and [`SchemaMigrator`](../../crm-persistence/src/main/java/com/minicrm/persistence/jdbc/SchemaMigrator.java):
   one authored migration, copied onto Flyway's classpath by Maven. Flyway records
   successful migrations in its schema-history table.
3. [`JdbcCustomerRepository`](../../crm-persistence/src/main/java/com/minicrm/persistence/jdbc/JdbcCustomerRepository.java):
   parameter binding, row mapping, and a customer-plus-tags transaction.
4. [`JdbcOpportunityRepository`](../../crm-persistence/src/main/java/com/minicrm/persistence/jdbc/JdbcOpportunityRepository.java):
   conditional updates and affected-row counts for optimistic concurrency.
5. [`JdbcActivityRepository`](../../crm-persistence/src/main/java/com/minicrm/persistence/jdbc/JdbcActivityRepository.java):
   subtype mapping and database-enforced idempotency.

## Runnable command

The user-selected setup is an existing PostgreSQL 16.4 Docker container, managed from
the command line. Testcontainers is not a dependency and tests do not manage Docker.

```sh
docker start minicrm-postgres
export JAVA_HOME="$PWD/.toolchain/jdk-25.0.4.1+1/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./scripts/ci.sh
```

Run from the repository root. The shown JDK path is the installed JDK on the reviewed
machine; on another machine use an installed Java 25 JDK. See the [README](../../README.md)
for the one-time container creation command.

Local defaults are `jdbc:postgresql://localhost:5432/minicrm`, username `postgres`,
password `test`. Override them through `CRM_DB_URL`, `CRM_DB_USERNAME`, and
`CRM_DB_PASSWORD`. These are development credentials.

[`PostgresTestSupport`](../../crm-persistence/src/test/java/com/minicrm/persistence/jdbc/support/PostgresTestSupport.java)
creates and migrates the dedicated `minicrm_test` schema. Tests truncate its CRM tables;
application tables in `public` are separate. The container and volume survive the test
run. Run only one suite at a time against this shared test schema. A successful test run
migrates the test schema, not the application's `public` schema.

## Observed result

Full Maven verification on the reviewed working tree:

```text
crm-domain:      Tests run: 51
crm-application: Tests run: 31
crm-persistence: Tests run: 35
Total: 117; failures: 0; errors: 0; skipped: 0
BUILD SUCCESS
```

The persistence total includes 20 JDBC tests and 15 existing tests. Evidence includes
round trips for all three activity subtypes, customer tags, contacts and opportunities;
PK/FK/UNIQUE and selected CHECK constraints; missing versus stale opportunity updates;
a two-writer optimistic-concurrency race; an eight-writer idempotency race; and a real
`pg_sleep(5)` query cancelled by a one-second JDBC query timeout.

This verifies local PostgreSQL only. It does not establish a resource-leak guarantee,
transaction rollback behavior under failure, or Lakebase credential refresh.

## Explanation

### Transactions and resource ownership

Customer save disables autocommit, upserts the customer, replaces its tags, and commits.
A caught SQL failure triggers rollback. The intended invariant is that both the customer
row and tag set change together. A connection scope is not itself a transaction:
autocommit and commit/rollback determine the database boundary.

Connections, statements, and result sets use try-with-resources. Closing a pooled
connection returns it to the pool. Pool acquisition timeout and statement timeout bound
different waits; neither should be described as an overall request deadline.

### Optimistic concurrency

The domain increments an opportunity's version when changing stage. JDBC saves the new
version only where the stored version equals the expected prior version. Two callers
that both loaded version 0 cannot both replace it with version 1. The integration race
asserts one success, one `StaleVersionException`, and a final version of 1. A zero-row
update followed by an absent row produces `OpportunityNotFoundException` instead.

### Idempotency and subtype mapping

The unique key is `(customer_id, idempotency_key)`. `INSERT ... ON CONFLICT DO NOTHING`
lets the database choose the winner atomically. A duplicate reads the stored activity
and request hash; application-service logic is responsible for rejecting a different
request hash. Repository duplicate tests alone do not verify an HTTP 409 response.

Activities use a discriminator and subtype columns rather than a JSON payload:
`CALL` stores duration, `EMAIL` stores body preview, and `MEETING` stores a PostgreSQL
text array of attendees. Pattern-matching switches bind the subtype fields and reconstruct
the appropriate record. Round-trip tests cover this representation; Java native object
serialization is not used.

## Fix / comparison

M3 used atomic map operations within one process. M4 moves opportunity conflict checks
and activity-key uniqueness into PostgreSQL, where independent connections share the
same rules. This is the meaningful comparison, rather than treating thread-safe Java
collections as database transactions.

During the preceding configuration work, the test harness was changed to connect to
the existing container, replacing its fixed-name temporary-container lifecycle. Two test
assertions were corrected: the search fixture `Other` also contained `e`, and PostgreSQL
reported cancellation as `PSQLException` with SQL state `57014`, not `SQLTimeoutException`.
The passing counts above include those corrections.

The review findings below remain open; this review did not change repository behavior.

## Review findings and follow-up work

### P2 — SQL limits customers before applying the port's ordering

`JdbcCustomerRepository.search` uses `ORDER BY name LIMIT ?`, then applies
`CustomerOrdering.BY_NAME` in Java. The in-memory implementation applies the shared
case-insensitive, UUID-tiebroken comparator before limiting. Sorting the selected subset
cannot recover rows already excluded by SQL.

A read-only SQL fixture on the current database with `Alpha`/UUID ending `001` and
`alpha`/UUID ending `002`, limited to one, selected `alpha`/`002`. The Java comparator
selects `Alpha`/`001`. Define a common ordering, including tie-breaking and supported
text/collation behavior, and enforce it before the limit in both adapters. Add a shared
adapter-contract test with mixed case, equal names, and a small limit.

### P2 — Search treats literal input as a LIKE pattern

`JdbcCustomerRepository.search` binds input into `ILIKE '%' || ? || '%'`. Parameters
prevent SQL injection but do not make `%` and `_` literal. A read-only SQL probe searching
for `%` returned both `Acme` and `Globex`; the in-memory `String.contains` implementation
returns neither. Escape pattern characters (including the escape character), or use a
literal substring expression, and add adapter-parity cases for `%`, `_`, and backslash.

### P2 — A failing concurrency worker can leave verification waiting indefinitely

`JdbcOpportunityRepositoryTest` calls untimed `barrier.await()` after a database read,
then uses untimed `invokeAll` and `Future.get`. If one worker fails before the barrier,
its peer can wait forever and the suite never reports the original failure. The activity
race also uses untimed coordination. Add bounded barrier/task waits and cancellation plus
executor termination in cleanup. A per-query timeout does not bound a Java barrier.

### Acceptance gap — Resource and transaction failure tests are missing

There is no injected tag-write failure proving that the customer upsert and tag deletion
roll back together, no pool-exhaustion/recovery assertion, and no resource-closure test on
exception paths. `java.sql.Array` objects in customer tag queries and activity mapping
also lack explicit `free()` calls; do not claim complete resource ownership from the
try-with-resources blocks alone. Add focused failure tests before marking the local
resource/transaction acceptance criterion complete.

The idempotency suite should additionally exercise different-content reuse through the
service backed by JDBC, key reuse by different customers, and retry after a simulated
lost response. Current tests cover successful subtype round trips and duplicate races.

### Acceptance gap — Databricks preparation and stable seed data are absent

The working tree contains the shared PostgreSQL DDL but no `crm-databricks` module,
`docs/databricks-setup.md`, `infra/databricks/config.example.env`, or
`databricks-{preflight,apply,smoke}.sh` / `sync-local-to-lakebase.sh` scripts. A committed
stable-ID synthetic seed dataset is also absent; current tests generate random IDs.

M4's remote preparation is pending. Later delivery automation is scheduled for M10,
but that does not make M4's connection/authentication and setup-guide work complete.
When remote work starts, follow the spec's free-service and bounded-smoke constraints;
record any availability blocker explicitly. No paid resource is needed for local progress.

## Remaining limitation

- The existing green suite misses the search-contract defects above.
- Activity subtype columns are nullable without conditional CHECK constraints enforcing
  the comment's “exactly one populated group” description. Valid round trips are tested;
  malformed subtype rows written outside Java are not.
- The default connection factory exists, but there is no application startup/profile
  wiring yet. REST delivery belongs to M5.
- Docker-based Maven fallback configuration was inspected but not executed in this
  review; evidence above uses the local Java 25 JDK.
- Lakebase SQL compatibility, authentication expiry/reconnection, and remote latency
  remain unverified. Local success must not be presented as remote evidence.

## Discussion prompts

1. Why does `PreparedStatement` prevent injection but still allow wildcard semantics?
2. Why does sorting after `LIMIT` produce the wrong first page?
3. Which writes must roll back if the second tag insertion fails?
4. What distinguishes a connection-acquisition timeout, query timeout, and task deadline?
5. After a lost response, how does an idempotency key let a caller discover the winner?
