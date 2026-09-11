# Spark distributed computing and runtime internals

## Status

**New, knowledge scaffolding only — no labs yet.** This folder tracks a learning
track distinct from `../data-engineering/`: that pipeline is written entirely in
Lakeflow Declarative Pipeline SQL, which deliberately hides the execution engine
(no partitioning, join strategy, or shuffle you touch directly). This folder is
for the layer underneath — the Spark execution model itself — scoped against
this project's Free Edition, zero-paid-cloud, serverless-only constraint
(design-spec §9).

## Knowledge areas

1. **Execution model.** `SparkSession`/`SparkContext`; driver vs. executors;
   how one query becomes a job → stages → tasks; wide vs. narrow
   transformations; DAG scheduler vs. task scheduler.
2. **Catalyst optimizer and Adaptive Query Execution (AQE).** Logical plan →
   optimized logical plan → physical plan; predicate/projection pushdown;
   AQE's runtime re-planning (coalescing shuffle partitions, switching join
   strategy, skew-join handling) after real statistics are known.
3. **Shuffle mechanics.** Shuffle write/read, the `Exchange` operator,
   partitioning schemes (hash/range/round-robin), `spark.sql.shuffle.partitions`,
   and spill to disk when a shuffle or aggregation outgrows execution memory.
4. **Join strategies.** Broadcast hash join, shuffle hash join, sort-merge
   join; the broadcast threshold; skew handling (AQE's skew-join optimization,
   manual salting).
5. **Memory management.** The unified memory manager, execution vs. storage
   regions, on-heap vs. off-heap, executor memory overhead, and what "spill"
   actually costs.
6. **Photon.** Databricks' native vectorized execution engine — which physical
   operators it replaces and where its behavior diverges from open-source
   Spark.
7. **Caching and persistence.** `cache()`/`persist()` storage levels; when
   caching helps vs. when it just hides a shuffle problem one layer down.
8. **Partition pruning and file skipping.** Delta's data-skipping stats,
   Z-ordering/liquid clustering vs. partition columns, the small-file problem
   and `OPTIMIZE`.
9. **Reading a plan.** `.explain(mode="formatted")` / `EXPLAIN FORMATTED`,
   Spark UI's DAG and SQL tabs (classic clusters only — see below), and
   Query Profile's stage/operator/shuffle/spill/skew view (serverless SQL
   warehouses).

## The serverless problem

Free Edition runs everything on serverless compute; it has no classic
all-purpose clusters to attach to. Two consequences, confirmed against current
Databricks docs and community reports:

- **No Spark UI.** The Spark UI is driver-hosted and exposes JVM/executor/DAG
  internals; Databricks disables it on serverless because the driver and
  executors are ephemeral and multi-tenant, not something scoped to one
  user's view.
- **Query Profile is the serverless substitute.** For SQL warehouse queries
  (Query History → a query → profile tab), you still get stages, operators,
  shuffle bytes, spill bytes, and skew flags — a simplified view over the same
  physical plan, just without the live task-timeline a classic cluster's Spark
  UI gives you. `EXPLAIN`/`EXPLAIN FORMATTED` works identically on serverless
  and classic, since it doesn't need the running driver.

So, within this project's Free Edition + zero-paid-cloud constraint, there is
no way to rent a classic cluster here for full Spark UI access.

## Practical learning path

1. **Run open-source Spark locally first — free, zero cloud dependency.**
   `pip install pyspark` (or a local Spark Docker image), then `pyspark` /
   `spark-shell` starts a local Spark UI at `localhost:4040` with the full
   DAG, stage/task timeline, SQL-tab physical plans, storage tab, and executor
   memory/GC view. This is where the internals actually become visible:
   write a deliberately skewed join or an oversized shuffle and watch the DAG
   and SQL tab change in response.
2. **Practice plan-reading — this is the one skill that transfers directly to
   serverless.** `.explain(mode="formatted")` locally, comparing the physical
   plan before/after a repartition, a broadcast hint, or a pushed-down filter.
   Query Profile on Free Edition is a GUI over this same physical plan, so a
   local habit of reading plans is exactly what carries over.
3. **Profile the pipeline you already have.** Once local intuition is built,
   use Query Profile against the `gold_*` queries in
   [`../data-engineering/src/gold/`](../data-engineering/src/gold/) on the
   Free Edition SQL warehouse. This closes the loop onto production-shaped SQL
   you already committed, rather than a synthetic query.
4. **Reproduce, locally, the two failure modes Query Profile calls out —
   spill and skew.** Undersize `spark.sql.shuffle.partitions` against a large
   join to force spill; use a heavily-skewed join key to force skew. Fix each
   (repartition, salting, AQE tuning) and diff the plan/metrics before and
   after. This is the same naive/failing/fixed/measured shape as the existing
   `docs/learning/session-NN.md` convention (e.g. session-04.md's
   Fix/comparison section).
5. **Only then re-verify against Free Edition.** Use Query Profile there to
   confirm — or find where Photon diverges from — the OSS Spark behavior
   already understood locally. Free Edition is for confirming a model against
   Databricks' actual planner/AQE/Photon behavior, not for discovering it
   first; serverless feedback is coarser and quota is bounded (design-spec §9).

## 

## Sources

- [Query profile](https://docs.databricks.com/aws/en/sql/user/queries/query-profile)
- [Skew and spill](https://docs.databricks.com/aws/en/optimizations/spark-ui-guide/long-spark-stage-page)
- [Spark stage high I/O](https://docs.databricks.com/aws/en/optimizations/spark-ui-guide/long-spark-stage-io)
- [Serverless compute limitations](https://docs.databricks.com/aws/en/compute/serverless/limitations)
- [Community: is Spark UI available on Databricks Free Edition?](https://community.databricks.com/t5/get-started-discussions/is-spark-ui-available-on-the-databricks-free-edition/td-p/123148)
- [Community: accessing Spark UI in Free Edition](https://community.databricks.com/t5/data-engineering/accessing-spark-ui-in-free-edition/td-p/134314)
