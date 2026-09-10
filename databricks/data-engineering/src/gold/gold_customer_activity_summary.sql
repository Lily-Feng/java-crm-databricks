-- Gold: one row per customer with open-pipeline exposure and activity recency --
-- the shape crm_get_customer_360 / crm_get_open_opportunities answer from the live
-- CRM (design-spec §10). `industry` is kept as a clustering/filter dimension since
-- it's the natural slice for an analytics dashboard over this table.
CREATE OR REFRESH MATERIALIZED VIEW gold_customer_activity_summary
COMMENT 'Per-customer open-pipeline exposure and activity recency.'
CLUSTER BY (industry)
AS
SELECT
  c.customer_id,
  c.name AS customer_name,
  c.industry,
  count(DISTINCT o.opportunity_id)
    FILTER (WHERE o.stage NOT IN ('CLOSED_WON', 'CLOSED_LOST')) AS open_opportunity_count,
  sum(o.amount_minor)
    FILTER (WHERE o.stage NOT IN ('CLOSED_WON', 'CLOSED_LOST')) AS open_amount_minor,
  count(DISTINCT a.activity_id) AS activity_count,
  max(a.occurred_at) AS last_activity_at
FROM silver_customers c
LEFT JOIN silver_opportunities o ON o.customer_id = c.customer_id
LEFT JOIN silver_activities a ON a.customer_id = c.customer_id
GROUP BY c.customer_id, c.name, c.industry;
