-- Gold: pipeline totals by stage/owner/currency -- the exact dimensions the
-- GET /dashboard/pipeline REST endpoint and crm_pipeline_summary MCP tool report
-- (design-spec §6, §10). Kept ungrouped further than that so any dashboard filter
-- on stage, owner, or currency can be answered directly; further rollups (e.g. by
-- owner only) are cheap to compute from this table.
CREATE OR REFRESH MATERIALIZED VIEW gold_pipeline_summary
COMMENT 'Pipeline totals by stage/owner/currency.'
CLUSTER BY (stage, owner, currency)
AS
SELECT
  stage,
  owner,
  currency,
  count(*) AS opportunity_count,
  sum(amount_minor) AS total_amount_minor,
  sum(cast(amount_minor * probability AS BIGINT)) AS weighted_amount_minor,
  avg(probability) AS avg_probability
FROM silver_opportunities
GROUP BY stage, owner, currency;
