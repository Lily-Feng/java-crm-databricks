-- Bronze: raw, append-only history of `contacts` snapshot exports.
-- See bronze_customers.sql for the landing/ingestion convention.
CREATE OR REFRESH STREAMING TABLE bronze_contacts
COMMENT 'Raw contact snapshot rows, one file per scripts/analytics-snapshot.sh run.'
AS
SELECT
  *,
  _metadata.file_path AS _source_file,
  current_timestamp() AS _ingested_at
FROM STREAM read_files(
  '${landing_volume}/contacts',
  format => 'json',
  schemaHints => 'contact_id STRING, customer_id STRING, name STRING, email STRING, role STRING, snapshot_id STRING, data_as_of TIMESTAMP'
);
