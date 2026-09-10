-- Bronze: raw, append-only history of `customers` snapshot exports.
-- Every run of scripts/analytics-snapshot.sh lands one JSON file per table under
-- ${landing_volume}; each row here is exactly one (customer_id, snapshot_id) pair,
-- unchanged from the source. Nothing is deduplicated or typed-checked yet — that is
-- Silver's job. Keeping every snapshot (not just the latest) preserves the ability to
-- answer "what did we know as of snapshot X" for the CDC lesson in design-spec §9.3.
CREATE OR REFRESH STREAMING TABLE bronze_customers
COMMENT 'Raw customer snapshot rows, one file per scripts/analytics-snapshot.sh run.'
AS
SELECT
  *,
  _metadata.file_path AS _source_file,
  current_timestamp() AS _ingested_at
FROM STREAM read_files(
  '${landing_volume}/customers',
  format => 'json',
  schemaHints => 'customer_id STRING, name STRING, industry STRING, email STRING, created_at TIMESTAMP, updated_at TIMESTAMP, version INT, snapshot_id STRING, data_as_of TIMESTAMP'
);
