-- Separate completed operation receipts from clinical records; never retain raw request bodies.
CREATE TABLE ingestion_receipt (
    actor_id text NOT NULL,
    actor_role text NOT NULL CHECK (actor_role = 'CLINICIAN'),
    idempotency_key text NOT NULL CHECK (idempotency_key ~ '^[A-Za-z0-9._-]{1,128}$'),
    request_hash text NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    http_status integer NOT NULL CHECK (http_status IN (200,400)),
    response_body text NOT NULL,
    patient_ids jsonb NOT NULL CHECK (jsonb_typeof(patient_ids) = 'array'),
    completed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (actor_id, actor_role, idempotency_key)
);

ALTER TABLE patient ADD COLUMN demographic_provenance jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE access_log ADD COLUMN actor_roster_verified boolean NOT NULL DEFAULT false;
ALTER TABLE encounter ADD COLUMN fhir_status text;
-- PostgreSQL stores microseconds: the remainder preserves exact supported nanosecond instants.
ALTER TABLE observation ADD COLUMN effective_submicro_nanos smallint NOT NULL DEFAULT 0
    CHECK (effective_submicro_nanos BETWEEN 0 AND 999);
ALTER TABLE observation ADD COLUMN source_interpretation jsonb NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE observation_review (
    observation_id text PRIMARY KEY REFERENCES observation(id),
    criticality text NOT NULL CHECK (criticality IN ('UNKNOWN','NONCRITICAL','CRITICAL')),
    reviewed_by text REFERENCES actor(id),
    reviewed_at timestamptz,
    critical_path_by text REFERENCES actor(id),
    critical_path_at timestamptz,
    released_by text REFERENCES actor(id),
    released_at timestamptz,
    CHECK ((reviewed_by IS NULL) = (reviewed_at IS NULL)),
    CHECK ((critical_path_by IS NULL) = (critical_path_at IS NULL)),
    CHECK ((released_by IS NULL) = (released_at IS NULL)),
    CHECK (released_at IS NULL OR (reviewed_at IS NOT NULL AND criticality <> 'UNKNOWN'
        AND (criticality <> 'CRITICAL' OR critical_path_at IS NOT NULL)))
);

CREATE INDEX observation_exact_time_idx ON observation (patient_id, effective DESC, effective_submicro_nanos DESC, id);
CREATE INDEX observation_exact_trend_idx ON observation (patient_id, code, effective, effective_submicro_nanos, id);
DROP INDEX observation_patient_time_idx;
DROP INDEX observation_patient_code_time_idx;
REVOKE ALL ON ingestion_receipt, observation_review FROM PUBLIC;
GRANT SELECT, INSERT ON ingestion_receipt, observation_review TO atlas_runtime;
-- Receipts are immutable. Runtime still cannot update/delete a clinical observation or audit row.
