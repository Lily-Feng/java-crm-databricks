-- Bronze: raw, append-only history of `opportunities` snapshot exports.
-- See bronze_customers.sql for the landing/ingestion convention.
CREATE OR REFRESH STREAMING TABLE bronze_opportunities
COMMENT 'Raw opportunity snapshot rows, one file per scripts/analytics-snapshot.sh run.'
AS
SELECT
  *,
  _metadata.file_path AS _source_file,
  current_timestamp() AS _ingested_at
FROM STREAM read_files(
  '${landing_volume}/opportunities',
  format => 'json',
  schemaHints => 'opportunity_id STRING, customer_id STRING, name STRING, amount_minor BIGINT, currency STRING, stage STRING, probability DOUBLE, owner STRING, notes STRING, version INT, created_at TIMESTAMP, updated_at TIMESTAMP, snapshot_id STRING, data_as_of TIMESTAMP'
);
