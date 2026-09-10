-- Bronze: raw, append-only history of `activities` snapshot exports.
-- See bronze_customers.sql for the landing/ingestion convention. `activities` rows
-- are immutable once created in Postgres (§5.1), so unlike the other tables a given
-- activity_id never changes across snapshots -- it either hasn't been exported yet
-- or is present as-is.
CREATE OR REFRESH STREAMING TABLE bronze_activities
COMMENT 'Raw activity snapshot rows, one file per scripts/analytics-snapshot.sh run.'
AS
SELECT
  *,
  _metadata.file_path AS _source_file,
  current_timestamp() AS _ingested_at
FROM STREAM read_files(
  '${landing_volume}/activities',
  format => 'json',
  schemaHints => 'activity_id STRING, customer_id STRING, type STRING, subject STRING, occurred_at TIMESTAMP, created_by STRING, duration_minutes INT, body_preview STRING, attendees ARRAY<STRING>, idempotency_key STRING, request_hash STRING, snapshot_id STRING, data_as_of TIMESTAMP'
);
