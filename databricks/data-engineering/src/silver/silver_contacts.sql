-- Silver: one current row per contact -- the latest snapshot observed for each
-- contact_id.
CREATE OR REFRESH MATERIALIZED VIEW silver_contacts (
  CONSTRAINT contact_id_present EXPECT (contact_id IS NOT NULL) ON VIOLATION DROP ROW,
  CONSTRAINT customer_id_present EXPECT (customer_id IS NOT NULL) ON VIOLATION DROP ROW
)
COMMENT 'Latest known state per contact, deduplicated across snapshot runs.'
CLUSTER BY (customer_id)
AS
SELECT
  contact_id,
  customer_id,
  name,
  email,
  role,
  snapshot_id,
  data_as_of
FROM bronze_contacts
QUALIFY row_number() OVER (PARTITION BY contact_id ORDER BY data_as_of DESC) = 1;
