-- Silver: distinct customer tags as observed in the most recent snapshot run.
-- `tags` is a pure membership table with no id/updated_at of its own, so "current
-- state" means "present in the latest snapshot_id" rather than a per-row dedupe.
CREATE OR REFRESH MATERIALIZED VIEW silver_tags
COMMENT 'Customer tags as of the latest snapshot run.'
AS
SELECT DISTINCT customer_id, tag
FROM bronze_tags
QUALIFY snapshot_id = max(snapshot_id) OVER ();
