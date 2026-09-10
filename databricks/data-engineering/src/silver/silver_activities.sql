-- Silver: one current row per activity. Activities are immutable in Postgres
-- (§5.1), so "latest snapshot per activity_id" is just the first export that
-- picked it up -- this still guards against a snapshot script rerun landing the
-- same activity twice.
CREATE OR REFRESH MATERIALIZED VIEW silver_activities (
  CONSTRAINT activity_id_present EXPECT (activity_id IS NOT NULL) ON VIOLATION DROP ROW,
  CONSTRAINT customer_id_present EXPECT (customer_id IS NOT NULL) ON VIOLATION DROP ROW,
  CONSTRAINT valid_type EXPECT (type IN ('CALL', 'EMAIL', 'MEETING')) ON VIOLATION DROP ROW
)
COMMENT 'One row per activity, deduplicated across snapshot re-exports.'
CLUSTER BY (customer_id, type)
AS
SELECT
  activity_id,
  customer_id,
  type,
  subject,
  occurred_at,
  created_by,
  duration_minutes,
  body_preview,
  attendees,
  idempotency_key,
  snapshot_id,
  data_as_of
FROM bronze_activities
QUALIFY row_number() OVER (PARTITION BY activity_id ORDER BY data_as_of DESC) = 1;
