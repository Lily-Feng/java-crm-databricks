# CRM medallion pipeline — bronze / silver / gold

## Status

**New, unvalidated.** This is a Lakeflow Declarative Pipeline (SDP, formerly DLT)
scaffolded on top of the design in `spec/design-spec.md` §9.3, not yet deployed or
run against a real Databricks workspace. Treat it the same way `docs/learning/`
treats pending Databricks work: the SQL is written to the documented API and
reviewed for correctness, but "it deploys and runs clean on Free Edition" is not
yet claimed. See [Deploy and run](#deploy-and-run) for what's still needed before
that's true, in particular the landing Volume and an extended snapshot script.

## Concept: the medallion pattern

Bronze/silver/gold ("medallion architecture") is a convention for structuring a
data pipeline as three progressively-cleaner layers, each a real table you can
query and debug independently:

| Layer  | Contains                                              | Shape |
|--------|--------------------------------------------------------|-------|
| Bronze | Raw source data, as ingested, unmodified               | Append-only history — every ingest run adds rows, nothing is overwritten |
| Silver | Cleaned, deduplicated, typed, validated "current state" | One row per business key |
| Gold   | Aggregated, business-facing tables shaped for a specific dashboard or question | Grouped by the dimensions consumers actually filter on |

The point of keeping Bronze as raw, unmodified, append-only history (rather than
cleaning on ingest) is that a silver/gold bug can always be fixed by re-deriving
from Bronze — the raw data was never thrown away or overwritten in place.

## How this maps onto MiniCRM

MiniCRM's only existing analytics-movement design is the manually triggered
snapshot copy in design-spec §9.3: `scripts/analytics-snapshot.sh` reads a
consistent Lakebase snapshot and writes current rows to Delta, tagged with a
`snapshot_id` and `data_as_of`. This pipeline extends that idea rather than
replacing it — it's still snapshot-based batch copying, not CDC (design-spec is
explicit that continuous CDC is a later, optional lesson) — but generalizes it
from "just `opportunities`" to all five CRM tables, and splits "raw copy" from
"current state" into their own layers:

- **Bronze** (`src/bronze/`) — one streaming table per Postgres table
  (`customers`, `contacts`, `opportunities`, `activities`, `tags`), ingesting
  JSON files an extended snapshot script lands under a landing Volume, one file
  per table per run. Every snapshot run's rows are kept, tagged with
  `snapshot_id` / `data_as_of`, exactly like the existing `opportunities` design.
- **Silver** (`src/silver/`) — one materialized view per table, picking the
  latest `data_as_of` per business key (`customer_id`, `opportunity_id`, ...) and
  re-asserting the invariants Postgres enforces with `NOT NULL`/`CHECK` in
  [`V1__init.sql`](../../infra/databricks/sql/postgres/V1__init.sql) — Delta's
  own constraints are informational only (design-spec §4), so if this layer
  doesn't check `stage IN (...)` or `probability BETWEEN 0 AND 1`, nothing does.
- **Gold** (`src/gold/`) — two tables shaped for the CRM's own read paths, so
  the medallion pipeline actually answers a question MiniCRM already asks:
  - `gold_pipeline_summary` — pipeline totals by stage/owner/currency, mirroring
    `GET /dashboard/pipeline` and the `crm_pipeline_summary` MCP tool (§6, §10).
  - `gold_customer_activity_summary` — open-opportunity exposure and activity
    recency per customer, mirroring `crm_get_customer_360` /
    `crm_get_open_opportunities` (§10).

## Layout

```
databricks/data-engineering/
├── README.md                              this file
├── resources/
│   └── crm_medallion.pipeline.yml         pipeline resource (Workflow B: added to the
│                                           existing top-level bundle via `include:`)
└── src/
    ├── bronze/    bronze_customers.sql, bronze_contacts.sql, bronze_opportunities.sql,
    │              bronze_activities.sql, bronze_tags.sql
    ├── silver/    silver_customers.sql, silver_contacts.sql, silver_opportunities.sql,
    │              silver_activities.sql, silver_tags.sql
    └── gold/      gold_pipeline_summary.sql, gold_customer_activity_summary.sql
```

One dataset per file, named after the dataset, per the project's SDP convention.

## Prerequisites

1. **A Unity Catalog catalog and schema.** `../../databricks.yml` (gitignored —
   it's local per-developer config, per this repo's convention) declares
   `catalog` (defaults to `workspace`, this Free Edition workspace's default
   managed catalog — override if yours differs) and `schema` (defaults to
   `crm_data_engineering`, overridden to `${workspace.current_user.short_name}_crm_dev`
   under the `dev` target). `databricks bundle validate -t dev` confirms the bundle
   resolves cleanly with these defaults.
2. **A landing Volume** at `/Volumes/<catalog>/<schema>/crm_landing/` with one
   subdirectory per table (`customers/`, `contacts/`, `opportunities/`,
   `activities/`, `tags/`). Not yet created by any script in this repo — create it
   once (e.g. `CREATE VOLUME IF NOT EXISTS <catalog>.<schema>.crm_landing`).
3. **An extended `scripts/analytics-snapshot.sh`.** The existing script (design-spec
   §9.3) only copies `opportunities` directly to Delta. Bronze here expects it (or a
   sibling script) to instead write one JSON file per table per run under the
   landing Volume above, with every row carrying `snapshot_id` and `data_as_of`
   columns matching each table's Postgres schema in
   [`V1__init.sql`](../../infra/databricks/sql/postgres/V1__init.sql). That
   script change is not part of this pipeline and still needs to be written.

## Deploy and run

Standard Workflow-B bundle commands from the repo root:

```sh
databricks bundle validate -t dev
databricks bundle deploy -t dev
databricks bundle run crm_medallion -t dev
```

Bronze tables backfill from whatever is already under `crm_landing/` the first
time they run; after that each `databricks bundle run` only picks up new files.
Silver and Gold are materialized views and fully recompute from Bronze/Silver on
each refresh (small tables at CRM-lesson scale, so this is cheap).

## Design choices worth knowing before extending this

- **Materialized-view dedup instead of `AUTO CDC`.** Auto CDC (`APPLY AS CHANGES`)
  is the idiomatic way to turn a change feed into a current-state table, but it
  wants an actual change feed (a stream of inserts/updates/deletes) or, for
  periodic snapshots, `dp.create_auto_cdc_from_snapshot_flow` — Python-only, and
  a bigger jump than this SQL-first pipeline needs. Since every Bronze row
  already carries `data_as_of`, `QUALIFY row_number() OVER (PARTITION BY
  <key> ORDER BY data_as_of DESC) = 1` in each Silver view gets the same
  "latest row per key" result with plain SQL. Revisit Auto CDC if/when the CDC
  lesson in design-spec §9.3 actually lands a real change feed.
- **`tags` has no independent id or `updated_at`.** It's a `(customer_id, tag)`
  membership table, so `silver_tags` dedupes by "present in the latest
  `snapshot_id`" rather than by row.
- **Gold keeps dimensions dashboards actually filter on.** Both Gold tables
  group by exactly the columns their mirrored MCP tool/REST endpoint expose
  (stage/owner/currency; customer/industry) rather than pre-aggregating those
  away — further rollups are a cheap `GROUP BY` away from here, but a dropped
  dimension is not recoverable without going back to Silver.
