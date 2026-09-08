# MiniCRM — Software Design Specification

Status: draft v0.3 — reviewed against [idea.md](idea.md) and the learning, cost, Docker CI/CD, Databricks setup, and local MCP requirements on 2026-09-07.

This document specifies work to implement. Commands, workflows, and deliverable paths below are acceptance contracts; they do not imply that the application or infrastructure already exists. `idea.md` remains the source of the curriculum.

## 1. Purpose and scope

MiniCRM is a small Java learning project: Customer 360 + Opportunity Management, implemented as a modular monolith with REST and MCP adapters. Success means being able to explain, reproduce, fix, and measure enterprise Java problems. Keep the eight business operations from idea.md; infrastructure supports those exercises.

**Required outcomes:**

- Preserve the domain-first sequence, collections/generics/Streams review, five Customer360 concurrency implementations, failure laboratory, resilience, and observability from idea.md.
- Run the complete core CRM, read-only MCP tools, tests, and load experiments locally without a cloud account or paid API.
- Practice production delivery with Docker build, verification, local deployment, health checks, and rollback through CI/CD.
- Provide a Lakebase transactional adapter, a Databricks SQL Warehouse analytics adapter, and repeatable Free Edition setup and smoke-test pipelines. Remote execution is explicitly selected and never required for the default build or local runtime.
- Require **$0 in paid cloud services**. If a free service is unavailable or exhausted, stop that integration and continue locally; never upgrade or provision a paid replacement.

### 1.1 Scope decisions and changes from v0.1

**Lakebase is the intended transactional database in the Databricks profile.** It addresses the latency mismatch of routing interactive CRM reads/writes through an analytical SQL warehouse. Customer360, customer/activity operations, and opportunity updates use Lakebase; the analytics dashboard uses SQL Warehouse after a separate data-copy step. This intentionally adapts idea.md's storage choice while preserving its Java/JDBC/concurrency curriculum. Lakebase remains a finite remote dependency, so pooling, deadlines, cancellation, and downstream capacity still matter.

Local Postgres provides the persistent Docker fallback and transaction tests without cloud access. The original in-memory implementation remains the starting point. Test-only delays preserve the sequential-versus-concurrent teaching experiment even when ordinary transactional reads are fast. A direct-to-warehouse CRM adapter can be an optional comparison lab, not the main application path.

Local MCP and CI/CD are required through Milestone 12. Milestones 13–14 complete the original curriculum. Copying operational data to analytics is required; a small manually triggered snapshot is the initial implementation. Native CDC is an extension subject to free-tier capability checks (§9.3). The Statement Execution adapter is also an extension.

“Production learning” means repeatable local delivery and operational practice. It does not require commercial hosting or a production SLA.

### 1.2 Out of scope

UI, extra CRUD endpoints, multi-tenancy, Kubernetes, microservices, paid cloud compute/storage/registries, managed monitoring, hosted LLM calls, and automatic cloud provisioning. Contacts and tags can initially be fixtures included in Customer 360. Do not expand the business scope to exercise every Java feature.

## 2. Learning contract and traceability

For each significant feature follow idea.md §19: implement a naive version → write a test/experiment → demonstrate the failure → explain it → fix it → measure again → record the lesson. Keep broken examples isolated from the runtime. A passing lab may assert the expected failure; the default build must remain green and bounded.

| Source in idea.md | Required implementation/evidence |
|---|---|
| §1–2, §12, final architecture | Same small domain, eight endpoints, modular monolith, adapters around Java application services |
| §4–6 | Pure Java before Spring; value types, generics/PECS, collections, unsafe map then safe atomic business updates |
| §3, §17 | Lakebase/Postgres JDBC for transactions and Databricks JDBC for analytics; parameterized SQL, resource ownership, timeout/pool limits, explicit comparison of database behavior |
| §7 | Same Customer360 operation in sequential, Executor/Future, CompletableFuture, virtual-thread, and isolated structured-concurrency versions |
| §8–10 | All 16 failure labs; JMM, cache publication, locks and coordination primitives; bounded reproductions |
| §11 | CRM analytics using loops then Streams; blocking `parallelStream()` experiment against local delayed adapters |
| §13–14 | Local MCP server, shared application services, concurrent synthetic MCP clients |
| §15–16 | Hand-built resilience first; logs, metrics, traces, JFR and load measurements |
| §18–20 | Fourteen milestones, runnable evidence after each, learning notes and `JAVA_REVIEW.md` links |

Create `JAVA_REVIEW.md` at Milestone 1 with the checklist from idea.md §20, linking each covered topic to code, a test/lab, and a short lesson. Include the broader topics from §4–5 and §9–11: initialization, exceptions, generics, collections, JMM, Streams, and locking. I/O/NIO, serialization, class loading, GC, and ScopedValue/context propagation can use focused labs without new CRM features. Mark topics honestly as pending until there is evidence.

Every milestone adds `docs/learning/session-NN.md`: concept, runnable command, observed result, explanation, fix/comparison, and remaining limitation. Early runnable artifacts are tests or a CLI; Docker service delivery starts at Milestone 5.

## 3. Domain and API

```text
Customer
 ├── id, name, industry, email, createdAt, updatedAt, version
 ├── Contacts
 ├── Activities: Call | Email | Meeting
 ├── Opportunities
 └── Tags

Opportunity
 └── id, customerId, name, amount, stage, probability, owner, notes, version
```

Use records for IDs/value types and a sealed `Activity` hierarchy with pattern-matching `switch`. Domain code has no Spring or JDBC dependencies. Validate email's basic teaching invariant (`@`), non-null IDs, non-negative money, and probability in [0, 1]. Model money as currency plus integer minor units, never `double`; aggregate different currencies separately.

Allowed stage progression: `QUALIFIED → PROPOSAL → NEGOTIATION → CLOSED_WON`; any open stage may move to `CLOSED_LOST`. Closed stages are terminal in this project. Stage validation belongs in the domain. Mutable aggregates carry a version.

| Method | Path | Contract |
|---|---|---|
| POST | `/customers` | Create customer; `201`; no automatic retry guarantee |
| GET | `/customers/{id}` | Fetch customer; `404` if absent |
| GET | `/customers?search=...` | Bounded search results; pagination/limit documented |
| POST | `/customers/{id}/activities` | Create activity; require client-generated `Idempotency-Key` UUID |
| POST | `/opportunities` | Create opportunity; `201`; no automatic retry guarantee |
| PATCH | `/opportunities/{id}/stage` | Require `targetStage` and `expectedVersion`; stale version → `409` |
| GET | `/customers/{id}/360` | Aggregate customer, contacts/tags, opportunities, activities |
| GET | `/dashboard/pipeline` | Pipeline totals by stage/owner/currency from the selected adapter |

Use `application/problem+json` errors, validation errors as `400`, authentication failures as `401`, and a response trace identifier. Stage change example: `{ "targetStage": "PROPOSAL", "expectedVersion": 5 }` returns version 6 on success. Missing opportunity → `404`; invalid transition → `409` with a distinct problem type.

Customer360 has the same contract across implementations: missing customer → `404`; an essential branch failure fails the aggregate; timeout → `504`. Version C explicitly handles sibling cancellation and cleanup; no silent partial result. Never present three independently read branches as a guaranteed transactional snapshot.

## 4. Architecture and runtime profiles

```text
crm-domain       — domain only
crm-application  — domain + repository ports + application services
crm-persistence  — in-memory and local Postgres adapters
crm-databricks   — Lakebase connection/auth integration + SQL Warehouse analytics adapter
crm-mcp          — MCP tools calling application services
crm-api          — Spring Boot REST adapter and runtime composition root
crm-loadtest     — HTTP/MCP client harness and isolated concurrency/preview labs
```

Both persistence modules depend on application ports and the domain. Application code never imports adapters. `crm-api` wires the chosen persistence adapter and MCP transport at startup; its controllers call services only. `crm-loadtest` exercises the running service through HTTP/MCP without importing server internals. Enforce dependency direction with ArchUnit.

| Profile | Storage and use | Remote dependency |
|---|---|---|
| `in-memory` | Original collections/concurrency lessons; disposable fixtures | None |
| `local` (default) | Postgres in Docker; all eight endpoints and local MCP | None |
| `databricks-free` | Lakebase for CRM transactions/Customer360; SQL Warehouse for the copied analytics dataset | Explicitly configured Free Edition workspace |

A startup selects one profile. Missing Databricks credentials must not break `local`. Invalid remote configuration fails clearly, without silently switching stores. Switching back to local is an explicit action; local and remote profiles do not automatically synchronize data. Within the remote profile, analytics copying is an explicit operation (§9.3).

Use Java 25, Maven Wrapper, Spring Boot introduced at Milestone 5, JUnit/AssertJ, and Testcontainers where useful. Pin tested dependency versions and container digests during implementation. Keep preview code in a separately activated lab module/profile, outside the default application artifact. Java 25 structured concurrency is a preview API; compile, test, and run that lab with the same JDK and `--enable-preview`. Do not assume a future finalization date. [Java 25 preview APIs](https://docs.oracle.com/en/java/javase/25/docs/api/preview-list.html).

## 5. Persistence and correctness

### 5.1 Shared logical schema

```text
customers(customer_id, name, industry, email, created_at, updated_at, version)
contacts(contact_id, customer_id, name, email, role)
opportunities(opportunity_id, customer_id, name, amount_minor, currency,
              stage, probability, owner, notes, version, created_at, updated_at)
activities(activity_id, customer_id, type, subject, occurred_at, created_by,
           idempotency_key, request_hash)
tags(customer_id, tag)
```

Activity persistence must include each subtype's required fields or a versioned payload with tested serialization. Maintain separate Postgres and Databricks DDL; do not assume compatible SQL dialects. Commit tiny, synthetic seed datasets with stable IDs. Avoid production/customer data.

### 5.2 Local correctness baseline

Postgres enforces PK/FK/UNIQUE and supplies real transactions. In-memory repositories use atomic map operations/locks for equivalent single-process business invariants. Keep naive implementations separately for labs.

For opportunity updates:

```sql
UPDATE opportunities
SET stage = ?, version = version + 1, updated_at = ?
WHERE opportunity_id = ? AND version = ?
```

Check affected rows and distinguish missing entity from stale version. Concurrent same-version updates must produce one success and one conflict in the local integration test.

Scope activity idempotency to `(customer_id, idempotency_key)` with a Postgres unique constraint. Insert and enforce the key in the same transaction. On a duplicate, complete/roll back the failed transaction before retrieving the original activity. Same key and request hash returns the original result; same key with different content → `409`. Test concurrent duplicate calls and retry after an ambiguous response.

### 5.3 Lakebase transactions and Warehouse analytics

Use the PostgreSQL JDBC driver for Lakebase and share the SQL/repository implementation with local Postgres where compatible. Keep Lakebase authentication/configuration in `crm-databricks`. Use a bounded HikariCP pool, parameterized statements, query deadlines, and `try-with-resources`. Verify Lakebase's supported credential flow for the selected project type; refresh credentials for new physical connections and test expiration/reconnection. A local Postgres test does not verify that authentication path. [Lakebase connections](https://docs.databricks.com/aws/en/oltp/projects/connect).

Apply §5.2's transactional version and idempotency rules to Lakebase. All interactive CRM operations, including Customer360 and MCP reads, use this path. Measure actual latency, including cold starts and connection acquisition; do not promise a fixed millisecond response time.

Use Databricks JDBC 3.x for `WarehouseDashboardRepository`, which reads the copied analytics dataset in §9.3. Maintain separate pools and limits for Lakebase and Warehouse. Analytics slowness must not consume the transactional pool. Parameterize query values and allowlist configured catalog/schema identifiers. [Databricks JDBC driver](https://docs.databricks.com/aws/en/integrations/jdbc-oss/).

Delta primary, foreign, and unique keys are informational; `RELY` does not enforce uniqueness. `NOT NULL` and `CHECK` are enforced. These differences belong in the comparison lesson; application transaction correctness relies on Postgres/Lakebase, not informational Delta keys. [Databricks constraints](https://docs.databricks.com/aws/en/tables/constraints).

The initial dashboard may read Lakebase directly until the analytics adapter is implemented at Milestone 10. Thereafter the remote dashboard returns `dataAsOf` and `source` so stale snapshots are visible. A failed copy preserves the previous successful snapshot without blocking CRM transactions. Local mode continues to compute the dashboard from local Postgres.

A later Statement Execution API adapter replaces only the analytics adapter and exercises submit/poll/cancel with bounded result handling. [Statement Execution API](https://docs.databricks.com/aws/en/dev-tools/sql-execution-tutorial).

## 6. Customer360 concurrency progression

Keep all five implementations selectable for comparisons. Inject the illustrative 250/300/400 ms delays via a test-only adapter decorator, off by default; these are synthetic inputs, not claimed database latencies.

| Version | Mechanism | Evidence |
|---|---|---|
| A | Sequential | Baseline latency near the sum of branch delays |
| B | ExecutorService, Callable, Future | Fan-out latency trends toward the slowest branch; bounded pool, shutdown/interruption tests |
| C | CompletableFuture | Composition and error-handling examples; explicit executor; aggregate deadline and sibling cleanup tests |
| D | Virtual threads | Same results, different throughput/resource behavior; Semaphore limits downstream concurrency |
| E | Structured concurrency lab | Failing child cancels siblings; scope terminates within a documented bound |

Do not make a ±10% wall-clock benchmark a correctness assertion. Record warm-up, repetitions and resource settings. Test cancellation with controlled fakes, then measure actual JDBC cancellation separately: cancelling a Java future alone does not prove a remote query stopped.

Start with a local pool of 10 and configurable bulkhead limits. Every retry reacquires a permit; release permits/connections on all completion paths. Use bounded queues and deadlines covering acquisition, query, retry, and aggregation. Do not pool virtual threads to limit database capacity.

Run 50/500/5,000/20,000-request experiments only locally as hardware permits. Stop on resource exhaustion; no new hardware is required. For Free Edition, use at most two outstanding statements (writes serialized), at most 20 statements per smoke run, and a five-minute overall deadline. Quota errors terminate the run without retries. Large-scale and retry-storm tests never target Databricks.

## 7. Failure laboratory, JMM, and resilience

Keep all 16 labs from idea.md §8, each with a reproduction command, bounded termination, explanation, and fixed comparison:

| Lab | Problem | Lab | Problem |
|---|---|---|---|
| 01 | Race condition | 09 | Retry storm |
| 02 | Lost update | 10 | Duplicate request |
| 03 | Unsafe publication | 11 | Partial failure |
| 04 | Memory visibility | 12 | Timeout propagation |
| 05 | Deadlock | 13 | Cancellation |
| 06 | Connection pool exhaustion | 14 | Unbounded queue |
| 07 | Thread pool starvation | 15 | ThreadLocal misuse |
| 08 | Cache stampede | 16 | Virtual threads vs downstream capacity |

Use in-memory/delayed repositories, fake connection pools, and local Postgres as appropriate. Isolate deadlocks and runaway workloads in child processes with hard timeouts. JMM outcomes can be nondeterministic: explain the forbidden assumption and repeat in a bounded harness rather than require an unsafe outcome on every CI run.

Review cache publication through `synchronized`, `volatile`, `AtomicReference`, and `ConcurrentHashMap`. Use focused labs for `ReentrantLock`, `ReadWriteLock`, `StampedLock`, `Condition`, `Semaphore`, `CountDownLatch`, `CyclicBarrier`, and `BlockingQueue`; do not force every primitive into application code.

Build resilience in idea.md's order: timeout → retry/backoff/jitter → circuit breaker → bulkhead → idempotency. Classify failures and bound attempts by the original deadline. A timeout/reset does not prove a write failed; retry reads or operations whose replay safety is established. Validation, stale-version conflicts, authentication failures, and quota exhaustion do not receive blind retries. Demonstrate retry amplification locally using synthetic requests. A library replacement is a later comparison.

## 8. Docker runtime for production learning

Milestones 1–4 run Java tests/CLIs on the host. From Milestone 5, the delivered service runs in Docker Compose, while tests and the load generator can stay host-side using the same pinned JDK. No Docker-in-Docker is required.

Required files: multi-stage `Dockerfile`, `.dockerignore`, `docker-compose.yml`, `.env.example`, `.gitignore`, and scripts described in §11. Build with Maven/JDK; run as a non-root user in a pinned Java runtime image. Provide a separate diagnostic JDK image target for `jcmd`/JFR labs. Secrets are injected at runtime and excluded from image layers/build context.

```text
Local Docker topology
  crm-api       REST + Streamable HTTP MCP in one JVM, host 127.0.0.1:8080
  postgres      internal Compose network, persistent named volume
  prometheus    optional observability profile, localhost access
  grafana       optional observability profile, localhost access
```

Publish `127.0.0.1:8080:8080`. Inside the container the app may listen on all container interfaces so the published port works. Do not expose Postgres externally by default. A dedicated Databricks Compose override selects `databricks-free` and removes the local database startup dependency.

Required runtime behavior: health/readiness checks, wait for DB readiness, versioned migrations, graceful SIGTERM with bounded request draining, restart behavior, and persistent local data. Readiness should not continuously query Databricks and consume quota; report cached initialization/connection state separately from liveness. Normal shutdown retains volumes; destructive demo-data reset is a separate explicit command.

Record container CPU/memory limits and Docker VM allocation. Set heap sizing with room for native memory and verify available processors in the container. Select limits within the laptop's capacity. Store recordings under a mounted `./jfr` directory; log the image version and selected profile for reproducibility.

A local Docker engine is sufficient. Docker Desktop's personal/education terms fit this learning use; no paid Docker Build Cloud, registry, or monitoring service is required. [Docker Personal](https://www.docker.com/products/personal/).

## 9. Databricks Free Edition setup

### 9.1 Cost boundary

Use **Databricks Free Edition**, not a credit-based trial or a paid workspace. Free Edition includes a managed workspace and default storage. Do not attach personal AWS/Azure/GCP billing, create cloud buckets/VMs, or upgrade to make a lesson work. A signup or feature requiring payment is outside this design. [Free Edition signup](https://docs.databricks.com/aws/en/getting-started/free-edition).

At review time the documented limits include one 2X-Small SQL warehouse and one Lakebase project. Free Edition is for non-commercial learning and has no SLA; quota exhaustion can suspend compute until the daily/monthly reset. Do not assume independent quota buckets or uninterrupted availability. Recheck the account's actual capabilities at setup. [Free Edition limitations](https://docs.databricks.com/aws/en/getting-started/free-edition-limitations).

The design guarantees a local path without paid cloud dependencies; future free-tier availability is outside the project's control. Remote integration evidence remains pending if the free workspace is unavailable. Do not report mocked or skipped tests as a successful real Databricks integration.

### 9.2 Setup deliverables and sequence

Commit `docs/databricks-setup.md`, `infra/databricks/sql/` (versioned DDL and seed scripts), `infra/databricks/config.example.env`, and `scripts/databricks-{preflight,apply,smoke}.sh`.

1. Create/sign into Free Edition manually. Record the edition and verification date without secrets. Use workspace-managed resources; no Terraform cloud-account setup.
2. Create the available free Lakebase project and record its project/branch, Postgres endpoint, database, and role. Select the SQL warehouse; record workspace host, warehouse ID, and JDBC HTTP path from its connection details. Use a writable existing catalog and a dedicated `mini_crm_dev` schema. Prefer auto-stop where supported; no keep-alive job.
3. Establish supported user authentication. Prefer OAuth U2M for local CLI setup; verify Lakebase database credential generation and Warehouse JDBC OAuth configuration separately. A CLI login is not automatically a JDBC credential. Document exactly how the chosen credential reaches the host script/container, expiry, and refresh. Use a scoped PAT only if the workspace supports it. Do not assume account APIs or service-principal creation are available. [Databricks user OAuth](https://docs.databricks.com/aws/en/dev-tools/auth/oauth-u2m).
4. Preflight checks the explicit `databricks-free` target, recorded Free Edition verification, allowlisted workspace host, valid auth for both stores, Lakebase database access, warehouse access, and intended database/catalog/schema. A URL alone cannot prove edition; fail if verification is missing. Never print credentials.
5. Apply versioned transactional DDL to the dedicated Lakebase schema and analytics DDL to the dedicated Delta schema. Keep a migration ledger; re-running successful setup is harmless. Resume failed nontransactional DDL explicitly rather than claiming whole-migration atomicity. Seed stable synthetic IDs without accumulating duplicates. No automatic `DROP`, `CREATE OR REPLACE` on populated tables, or unrelated schema changes.
6. Run the bounded smoke suite from §6: Lakebase connection/parameterized query, seed read, one create/read and versioned stage update, cleanup of disposable records, then analytics snapshot publication and a Warehouse dashboard read. Verify transactional constraints and the separate credential paths; record any limitation. Cleanup is best-effort within the same total request budget; report leftovers if quota/network access prevents it.
7. Start the local Docker app with the Databricks override. Exercise one REST and MCP read against the seeded customer. The CRM/MCP server remains on the laptop. Record the complete setup and smoke result, then stop the app; no scheduled traffic.

The deployment pipeline in §11 invokes these scripts. They apply Lakebase setup through PostgreSQL JDBC and analytics setup through Databricks JDBC/Statement API tooling from the local runner; they do not require a Databricks Job or remote access to the laptop's Postgres.

If a later lesson adds notebooks/jobs, keep their resource definitions in a Databricks bundle and validate/deploy only to the verified free target. Bundles are currently called Declarative Automation Bundles (formerly Asset Bundles). They are an extension, not a prerequisite to creating this small SQL schema. [Databricks bundles](https://docs.databricks.com/aws/en/dev-tools/bundles/).

### 9.3 Moving data to analytics: snapshot first, CDC later

**CDC means Change Data Capture:** capturing inserts, updates, and deletes from the transactional database so another system can apply them. For this project the direction is Lakebase → Delta/SQL Warehouse. It does not make the original CRM request faster; Lakebase supplies that improvement, and asynchronous data movement lets analytics follow separately.

The required first implementation is a manually triggered `scripts/analytics-snapshot.sh` running on the laptop. Read a small consistent Lakebase snapshot, write the current opportunity rows with a new `snapshot_id` and `data_as_of` to Delta, validate the row count, then publish a completed-snapshot marker. Dashboard queries select only the latest completed snapshot. Serialize publishers and bound rows/statements per run. Updates appear as current row values and deletes disappear from the next snapshot; this is a full batch copy, not CDC. No incoming connection to the laptop, streaming cluster, or scheduled cloud job is needed. Include a tiny snapshot in the manual Databricks pipeline's smoke budget; larger refreshes use a separately bounded manual command.

Native Lakebase Change Data Feed (CDF) is one CDC implementation: it captures Postgres changes into Delta history tables. Its documented requirements currently exclude catalogs using default storage and direct Free Edition users to an external managed storage location. That requirement must be reconciled with the zero-paid-cloud rule before enabling it; do not create a paid bucket or upgrade. Use the snapshot path if a verified no-cost configuration is unavailable. [Lakebase CDF requirements](https://docs.databricks.com/aws/en/oltp/projects/lakebase-cdf).

For a later CDC lesson, reconstruct current state from ordered changes, handle deletes/replays, and measure lag. Do not aggregate raw change history as if every record were a distinct opportunity. A failed/stalled feed leaves CRM operations working and marks analytics as stale. Continuous CDC is not a prerequisite for Lakebase or the core Java lessons.

## 10. Local MCP server — required

Use the MCP Java SDK and expose read-only tools first:

```text
crm_search_customers
crm_get_customer
crm_get_customer_360
crm_get_open_opportunities
crm_pipeline_summary
```

These call the same application services, deadlines, and resource limits as REST. Default **Streamable HTTP** runs at `http://127.0.0.1:8080/mcp` inside the existing CRM JVM. REST and MCP therefore share the same in-memory state, connection pool, and bulkheads. Multiple local clients can connect; this does not require multiple CRM deployments.

Also provide a **stdio launch mode** for clients that spawn a subprocess, using the same application/MCP modules in a separate entry point. The client launches `docker run --rm -i ...` with no TTY; only MCP messages go to stdout, and all logs/banners go to stderr. A stdio process has its own services and limits: use it as an alternative runtime, or budget each process separately. Independent in-memory processes do not share data; use local Postgres for shared persistence. The default deployment remains one server process so REST/MCP share limits; separate stdio processes require separate pool budgets, while database constraints enforce shared transactional correctness. [MCP transport specification](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports).

Document a tested client configuration for each mode in `docs/mcp-local.md`: HTTP URL/header setup; stdio command/args/environment; profile selection; startup/shutdown and error diagnosis. HTTP transport validates Origin when present and requires the local bearer token; stdio relies on the local process boundary. Docker HTTP exposure remains loopback-only.

Required acceptance tests, using an SDK client without an LLM: initialize/negotiate, list tools and validate schemas, call all read tools, compare Customer360 with REST, handle invalid input/unknown customer, cancellation, and bounded simultaneous REST/MCP traffic. Verify stdio stdout contains no logs. Run synthetic multi-client load locally. No model subscription, API key, inference endpoint, tunnel, or cloud MCP host is required.

`crm_add_activity` is a later opt-in write tool with the same validation and idempotency contract as REST. The read-only core must work with all Databricks and LLM credentials unset.

## 11. CI/CD — required local delivery and Databricks setup

CI/CD begins with Java verification at Milestone 1 and gains Docker delivery at Milestone 5. Deployment means a tested image running in the laptop's Compose environment. Databricks delivery means applying reviewed schema/setup scripts to Free Edition; the Java service is not deployed there.

### 11.1 Zero-cost execution and triggers

Implement portable scripts as the source of pipeline logic, with GitHub Actions wrappers. The guaranteed local route runs these scripts directly with Git and Docker. For a private repository, a runner on the user's existing computer can automate trusted work without renting compute. Public PRs use standard GitHub-hosted Linux runners where permitted; untrusted PRs must never run on the personal self-hosted runner or access deployment credentials.

GitHub currently documents free self-hosted runner usage and free standard hosted runners for public repositories; private hosted minutes and storage have quotas, and larger runners are charged. Recheck the plan before enabling hosted execution. Keep hosted artifact uploads, remote caches, and registry publishing off by default. Use local images and reports, with bounded local retention. If private hosted CI is selected, require a verified setting that blocks paid overage; otherwise use local scripts/self-hosting. No hosted service is necessary to complete a delivery. [GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions).

| Workflow | Trigger and environment | Required stages |
|---|---|---|
| `ci.yml` | Push/PR; safe runner selected as above | Java verification, local Postgres integration, MCP tests once implemented, Docker build, Compose smoke, cleanup |
| `deploy-local.yml` | Manual trusted main-branch commit; personal local runner | Verify → build versioned image → deploy → readiness + REST/MCP smoke → record release or roll back |
| `databricks-free.yml` | Manual trusted main-branch commit only; local authenticated runner | Free target preflight → validate migration/config inputs → apply Lakebase/Delta DDL/seed → publish tiny analytics snapshot → bounded live smoke → report |

No cron/nightly cloud tests or Databricks secrets in PR CI. Deploy jobs serialize per environment. CI can cancel superseded checks; it must not cancel a migration halfway through to deploy another commit. Set job timeouts and default token permissions to read-only. Pin third-party actions to reviewed commit SHAs. Do not use a privileged PR trigger to check out untrusted code.

User OAuth may require interactive renewal before a manual Databricks job; expired credentials make the job fail with a clear re-login instruction. Do not promise unattended cloud deployment without a supported, tested credential method.

### 11.2 Pipeline script contracts

Required scripts and equivalent README commands:

```text
./scripts/ci.sh                         # ./mvnw -B verify + local integration + image/Compose smoke
./scripts/build-image.sh <revision>     # immutable revision tag; record image ID and architecture
./scripts/deploy-local.sh <revision>    # deploy tested local image, health check and smoke
./scripts/rollback-local.sh <revision>  # restore a retained image and verify it
./scripts/databricks-preflight.sh       # no mutation; verify explicit remote target/auth
./scripts/databricks-apply.sh           # reviewed schema and tiny seed data
./scripts/databricks-smoke.sh           # bounded live integration, separate from normal CI
```

`ci.sh` uses a unique disposable Compose project/volume, removes only its own resources on exit, and never modifies the running learning deployment. Tests execute on the runner's host JVM using its Docker daemon. In one runner job, test and deploy the exact built image ID; do not rebuild between smoke and promotion. A failed verification prevents deployment.

Tag images with the Git revision and record image ID, platform, migration version, and timestamp in local release metadata. No registry is needed when building and deploying on the same Docker daemon. Build the local target architecture explicitly; if CI tested a different architecture, build and smoke-test the laptop's image before promotion.

Local deployment retains the previous image and database volume. If readiness/smoke fails, restore that image and verify recovery. Use backward-compatible additive migrations so application rollback remains possible. Image rollback never reverses database changes; a destructive migration requires a separate backup/restore exercise and is outside this automatic pipeline.

Databricks DDL changes also use forward migrations; cleanup only removes tagged smoke fixtures. If setup fails, report the failed migration and preserve its evidence. Do not “roll back” by dropping the schema.

### 11.3 Delivery acceptance

Demonstrate one successful pipeline, one test failure that blocks delivery, one unhealthy image that triggers verified local rollback, and one repeatable Databricks setup/smoke run when Free Edition is available. A local-only result can pass the default CI gate while the live Databricks result is explicitly pending. Keep reports on the laptop and summarize workflow outcomes without paid artifact storage.

## 12. Auth, observability, and testing

REST and local HTTP MCP require a static development bearer token; health checks can expose minimal status without CRM data. This is a bounded auth lesson. Load local secrets from ignored files/environment, commit placeholders only, redact credentials from logs, and never bake them into images or echo them in workflows.

Add structured SLF4J logs, Micrometer metrics, and OpenTelemetry spans with local output/export only. Include trace/request ID, customer ID where applicable, thread name/type, repository duration, acquisition wait, retry count, and MCP duration. Keep customer IDs out of metric labels. Compare ThreadLocal context propagation with explicit/ScopedValue-based context in a lab. Capture one JFR recording plus a `jcmd`/thread-dump investigation using the diagnostic image.

| Test layer | Environment and frequency |
|---|---|
| Domain, application, safe concurrency tests | In-memory/fakes; every verification |
| JDBC correctness and migrations | Local Postgres Testcontainers; every verification |
| REST/MCP protocol and Docker smoke | Disposable local Compose environment; CI once implemented |
| Lakebase auth/transactions and Warehouse SQL/driver semantics | Free Edition only; manual bounded workflow |
| Load, destructive failure reproductions, preview labs | Explicit local commands; time/resource bounded |

Postgres tests prove the Postgres adapter. Mocks prove application behavior against a port. Neither proves Databricks SQL syntax, auth, transaction semantics, or query cancellation. Test skipped/live results must be reported distinctly.

Every load report under `docs/loadtests/` records revision, image/JDK versions, profile, injected delays, dataset, warm-up, concurrency/duration, throughput, p50/p95/p99, error breakdown, connection/query peaks, queue wait, platform/virtual thread counts, heap/GC observations, and container/VM resources. Separate read and write results. Explain the observed bottleneck rather than declaring virtual threads universally faster.

## 13. Delivery milestones

These retain idea.md's fourteen-session sequence. A session is a focused learning unit, not a guarantee that all its work fits one day. Databricks preparation occurs in Milestone 4 and is completed with delivery automation in Milestone 10; a quota outage must not block local progress.

| # | Deliverable | Definition of done |
|---|---|---|
| 1 | Pure Java domain + build CI | Value types/invariants and architecture test; Maven Wrapper/CI entry point; `JAVA_REVIEW.md`; no Spring |
| 2 | Repository ports + generics | Interface and PECS/collections examples tested |
| 3 | In-memory CRM + analytics | Eight-operation application support; loops vs Streams; naive vs atomic map exercise |
| 4 | JDBC + Databricks setup | Local Postgres adapter passes resource/transaction tests; Lakebase connection/auth integration, DDL, auth/preflight and setup guide; live evidence or explicit pending status |
| 5 | REST + Docker CI/CD | Eight endpoints, errors/auth; persistent local Compose; image verification/deployment/rollback demonstration |
| 6 | Customer360 A/B | Sequential and Executor/Future versions; comparable local report |
| 7 | Customer360 C | CompletableFuture version; deadline, failure and cleanup tests |
| 8 | Customer360 D | Virtual threads and shared bulkhead; locally measured bounded connections |
| 9 | Failure labs 01–10 | Bounded broken/fixed cases, JMM/cache/lock lessons linked in review matrix |
| 10 | Resilience + Databricks pipeline | Retry/backoff/jitter/breaker/idempotency tests; Warehouse dashboard adapter and snapshot copy; manual free-target apply/smoke workflow; no cloud load tests |
| 11 | Observability | Logs/metrics/traces, local load report, JFR and thread investigation |
| 12 | Local MCP | HTTP and stdio launch modes, five read tools, client configs, protocol tests and synthetic concurrency report |
| 13 | Customer360 E | Isolated Java 25 preview module; cancellation and context propagation comparison |
| 14 | Remaining labs + curriculum review | Labs 11–16; one-command failure CLI; all required review topics have evidence |

Milestones 1–12 are the runnable project checkpoint, including Docker CI/CD and local MCP. Milestones 13–14 complete the learning curriculum. Optional Statement Execution and native CDC extensions cannot delay these requirements; Lakebase is the intended remote transactional store.

## 14. Final acceptance checklist

- [ ] The implementation maps to idea.md via §2 and `JAVA_REVIEW.md`; important lessons include naive/failing/fixed/measured evidence.
- [ ] With no Databricks or LLM credentials, a fresh checkout can verify Java, start local Docker CRM/Postgres, exercise all eight endpoints, and call five MCP tools.
- [ ] Local verification, images, delivery, rollback, observations, and load generation need no paid cloud service or registry.
- [ ] CI/CD workflows and portable scripts are committed and exercised, including failure and rollback paths.
- [ ] Databricks setup/config/DDL and a separate manual delivery workflow exist; live Free Edition success is recorded, or the specific unavailable capability remains visibly pending.
- [ ] Remote jobs cannot silently use paid targets; free quota/auth failures stop remote work and leave the local path usable.
- [ ] HTTP and stdio MCP work locally with synthetic SDK clients; REST/MCP share limits in the single-JVM deployment.
- [ ] All five concurrency versions and sixteen failure labs remain in the curriculum; preview code is isolated from the normal runtime.

## 15. Review findings resolved in this revision

| Finding in v0.1 | Resolution |
|---|---|
| v0.2 incorrectly made Lakebase optional while restoring the warehouse mismatch | Retain Lakebase for interactive transactions; preserve the Java curriculum through local and remote JDBC exercises; start analytics movement with a manual snapshot |
| MCP was optional, with no local launch contract | Required HTTP and stdio modes, tool list, client configs and acceptance tests |
| Docker packaging existed but CI/CD was absent | Add verification, local deployment, immutable images, rollback and separate Databricks delivery |
| Cloud availability blocked normal development | Default local profile and local testing; live free-tier results tracked separately |
| Free-tier buckets/prices and future JDK finalization were asserted without sufficient support | Remove speculative claims; cite current primary documentation and recheck at setup |
| Delta `RELY` was described as if it enforced constraints | Correct constraint semantics; transaction guarantees belong to Postgres/Lakebase |
| Runtime timing was contradictory; educational evidence was underspecified | Tests/CLIs first, Docker at Milestone 5, explicit traceability and lesson artifacts |

This review changes the specification only. No cloud resource, credential, runner, or application has been provisioned as part of the review.
