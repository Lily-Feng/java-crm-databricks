# Databricks architecture: the medallion pattern (bronze / silver / gold)

## Overview

Databricks organizes a lakehouse pipeline into three progressively-cleaner
layers — **bronze**, **silver**, and **gold** — known as the **medallion
architecture**. Each layer is a real, queryable Delta table; data moves
one-way from bronze through silver into gold, getting more structured,
validated, and business-shaped at each hop. It's a convention, not a
Databricks-enforced requirement, but it's the recommended default shape for
any non-trivial pipeline, and it's the shape this project's own CRM pipeline
follows (see [Applied in this project](#applied-in-this-project) below).

![Medallion architecture: bronze, silver, and gold layers of a Delta Lake pipeline](https://www.databricks.com/sites/default/files/inline-images/building-data-pipelines-with-delta-lake-120823.png)

Official reference: [Databricks docs — Medallion Lakehouse Architecture](https://docs.databricks.com/aws/en/lakehouse/medallion)

## The three layers

| Layer | Purpose | Shape | Typical consumers |
|---|---|---|---|
| **Bronze** — raw | Land source data with minimal processing | Append-only history; original formats/values kept as close to the source as possible, so nothing is lost before it's even validated | Data engineers, compliance/audit |
| **Silver** — validated | Clean, deduplicate, type, and quality-check | One validated, non-aggregated row per business key ("current state") | Data engineers, analysts, data scientists |
| **Gold** — enriched | Aggregate into business-facing, dashboard-shaped tables | Dimensional, pre-aggregated, grouped by the dimensions consumers actually filter on; usually far fewer tables than silver | BI developers, executives, ML engineers |

Why keep bronze raw and unmodified instead of cleaning on ingest: a bug in a
silver or gold transformation can always be fixed by re-deriving from bronze,
because the raw data underneath was never overwritten. Silver and gold are
disposable and rebuildable; bronze is the source of truth.

Ingestion into bronze can run **continuously** (streaming, lowest latency,
highest cost), **triggered** (event/schedule-driven, moderate on both), or
**batch** (least frequent, cheapest, highest latency) — the trade-off is
latency vs. cost, and a pipeline is free to pick differently per table.

## Applied in this project

MiniCRM's own medallion pipeline lives in
[`databricks/data-engineering/`](../data-engineering/README.md) and follows this
exact pattern, built on top of the snapshot-copy design in
[`spec/design-spec.md` §9.3](../../spec/design-spec.md):

| Layer | This project's tables | Source |
|---|---|---|
| Bronze | `bronze_customers`, `bronze_contacts`, `bronze_opportunities`, `bronze_activities`, `bronze_tags` | Raw JSON snapshot exports of the five Postgres/Lakebase tables in [`V1__init.sql`](../../infra/databricks/sql/postgres/V1__init.sql), landed one file per table per run |
| Silver | `silver_customers`, `silver_contacts`, `silver_opportunities`, `silver_activities`, `silver_tags` | Latest row per business key, with the same `NOT NULL`/`CHECK` invariants Postgres enforces (Delta's own constraints are informational only) |
| Gold | `gold_pipeline_summary`, `gold_customer_activity_summary` | Aggregated by stage/owner/currency and by customer/industry — the exact shapes the CRM's `GET /dashboard/pipeline` endpoint and `crm_pipeline_summary` / `crm_get_customer_360` MCP tools already expose |

See [`databricks/data-engineering/README.md`](../data-engineering/README.md) for
the full design (why materialized-view dedup instead of `AUTO CDC`, the landing
Volume convention, deploy/run commands) and the SQL itself under
[`databricks/data-engineering/src/`](../data-engineering/src/).

## See also

- [Databricks docs — Medallion Lakehouse Architecture](https://docs.databricks.com/aws/en/lakehouse/medallion)
- [`databricks/data-engineering/README.md`](../data-engineering/README.md) — this project's bronze/silver/gold pipeline
- [`spec/design-spec.md` §9.3](../../spec/design-spec.md) — "Moving data to analytics: snapshot first, CDC later"
