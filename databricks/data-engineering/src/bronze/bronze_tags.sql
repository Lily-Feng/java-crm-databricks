-- Bronze: raw, append-only history of `tags` snapshot exports.
-- `tags` has no primary key or updated_at of its own (§5.1) -- it's a plain
-- (customer_id, tag) membership table -- so Silver dedupes by latest snapshot_id
-- rather than by row id.
CREATE OR REFRESH STREAMING TABLE bronze_tags
COMMENT 'Raw tag snapshot rows, one file per scripts/analytics-snapshot.sh run.'
AS
SELECT
  *,
  _metadata.file_path AS _source_file,
  current_timestamp() AS _ingested_at
FROM STREAM read_files(
  '${landing_volume}/tags',
  format => 'json',
  schemaHints => 'customer_id STRING, tag STRING, snapshot_id STRING, data_as_of TIMESTAMP'
);
