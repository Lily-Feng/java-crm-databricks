-- MiniCRM shared Postgres schema (design-spec §5.1).
-- Applied unchanged to local Docker Postgres and to Lakebase (§4, §5.4) via Flyway.
-- This is the ONLY versioned DDL for this schema; do not fork a second copy for Lakebase.

CREATE TABLE customers (
    customer_id UUID PRIMARY KEY,
    name        TEXT NOT NULL,
    industry    TEXT NOT NULL DEFAULT '',
    email       TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE contacts (
    contact_id  UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers (customer_id),
    name        TEXT NOT NULL,
    email       TEXT NOT NULL,
    role        TEXT NOT NULL DEFAULT ''
);

CREATE INDEX contacts_customer_id_idx ON contacts (customer_id);

CREATE TABLE opportunities (
    opportunity_id UUID PRIMARY KEY,
    customer_id    UUID NOT NULL REFERENCES customers (customer_id),
    name           TEXT NOT NULL,
    amount_minor   BIGINT NOT NULL CHECK (amount_minor >= 0),
    currency       TEXT NOT NULL,
    stage          TEXT NOT NULL CHECK (stage IN
                       ('QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'CLOSED_WON', 'CLOSED_LOST')),
    probability    DOUBLE PRECISION NOT NULL CHECK (probability >= 0.0 AND probability <= 1.0),
    owner          TEXT NOT NULL,
    notes          TEXT NOT NULL DEFAULT '',
    version        INTEGER NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX opportunities_customer_id_idx ON opportunities (customer_id);

-- §5.1: "Activity persistence must include each subtype's required fields or a
-- versioned payload with tested serialization." Chose per-subtype columns (nullable,
-- exactly one populated group per row depending on `type`) over a JSON payload: it needs
-- no hand-rolled serialization code and no JSON library (Jackson doesn't arrive until
-- Spring does, Milestone 5), at the cost of three columns that are always partially NULL.
-- `attendees` uses a native Postgres array (java.sql.Array on the JDBC side).
CREATE TABLE activities (
    activity_id       UUID PRIMARY KEY,
    customer_id       UUID NOT NULL REFERENCES customers (customer_id),
    type              TEXT NOT NULL CHECK (type IN ('CALL', 'EMAIL', 'MEETING')),
    subject           TEXT NOT NULL,
    occurred_at       TIMESTAMPTZ NOT NULL,
    created_by        TEXT NOT NULL,
    duration_minutes  INTEGER,     -- CALL only
    body_preview      TEXT,        -- EMAIL only
    attendees         TEXT[],      -- MEETING only
    idempotency_key   TEXT NOT NULL,
    request_hash      TEXT NOT NULL,
    CONSTRAINT activities_idempotency_key_uk UNIQUE (customer_id, idempotency_key)
);

CREATE INDEX activities_customer_id_idx ON activities (customer_id);

CREATE TABLE tags (
    customer_id UUID NOT NULL REFERENCES customers (customer_id),
    tag         TEXT NOT NULL,
    PRIMARY KEY (customer_id, tag)
);
