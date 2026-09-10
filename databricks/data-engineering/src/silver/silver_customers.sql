-- Silver: one current row per customer -- the latest snapshot observed for each
-- customer_id, cast to real types, with the quality checks Postgres enforces via
-- NOT NULL/CHECK (infra/databricks/sql/postgres/V1__init.sql) re-applied here since
-- Delta constraints are informational only (design-spec §4).
CREATE OR REFRESH MATERIALIZED VIEW silver_customers (
  CONSTRAINT customer_id_present EXPECT (customer_id IS NOT NULL) ON VIOLATION DROP ROW,
  CONSTRAINT valid_email EXPECT (email IS NOT NULL AND email LIKE '%@%') ON VIOLATION DROP ROW
)
COMMENT 'Latest known state per customer, deduplicated across snapshot runs.'
CLUSTER BY (customer_id)
AS
SELECT
  customer_id,
  name,
  industry,
  email,
  created_at,
  updated_at,
  version,
  snapshot_id,
  data_as_of
FROM bronze_customers
QUALIFY row_number() OVER (PARTITION BY customer_id ORDER BY data_as_of DESC) = 1;
