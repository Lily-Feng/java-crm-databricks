-- Silver: one current row per opportunity -- the latest snapshot observed for each
-- opportunity_id, with the same stage/amount/probability invariants Postgres
-- enforces via CHECK constraints (infra/databricks/sql/postgres/V1__init.sql).
CREATE OR REFRESH MATERIALIZED VIEW silver_opportunities (
  CONSTRAINT opportunity_id_present EXPECT (opportunity_id IS NOT NULL) ON VIOLATION DROP ROW,
  CONSTRAINT valid_amount EXPECT (amount_minor >= 0) ON VIOLATION DROP ROW,
  CONSTRAINT valid_stage EXPECT (
    stage IN ('QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'CLOSED_WON', 'CLOSED_LOST')
  ) ON VIOLATION DROP ROW,
  CONSTRAINT valid_probability EXPECT (probability BETWEEN 0.0 AND 1.0) ON VIOLATION DROP ROW
)
COMMENT 'Latest known state per opportunity, deduplicated across snapshot runs.'
CLUSTER BY (customer_id, stage)
AS
SELECT
  opportunity_id,
  customer_id,
  name,
  amount_minor,
  currency,
  stage,
  probability,
  owner,
  notes,
  version,
  created_at,
  updated_at,
  snapshot_id,
  data_as_of
FROM bronze_opportunities
QUALIFY row_number() OVER (PARTITION BY opportunity_id ORDER BY data_as_of DESC) = 1;
