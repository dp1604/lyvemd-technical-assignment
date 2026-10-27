-- Source columns/PKs/FKs/nullables are retained. Other types/constraints are documented choices.
CREATE TABLE actor (
    id text PRIMARY KEY CHECK (id ~ '^[A-Za-z0-9._-]{1,64}$'),
    role text NOT NULL CHECK (role IN ('PATIENT', 'PROXY', 'CLINICIAN', 'STAFF'))
);

CREATE TABLE patient (
    id text PRIMARY KEY CHECK (id ~ '^[A-Za-z0-9.-]{1,64}$'),
    sex text CHECK (sex IN ('male', 'female', 'unknown')),
    birth_date date
);

CREATE TABLE patient_sex_context (
    patient_id text NOT NULL REFERENCES patient(id),
    valid_from date NOT NULL,
    valid_to_exclusive date,
    sex text NOT NULL CHECK (sex IN ('male', 'female', 'unknown')),
    PRIMARY KEY (patient_id, valid_from),
    CHECK (valid_to_exclusive IS NULL OR valid_to_exclusive > valid_from)
);
-- Cross-interval overlap is also checked under the patient's ingestion lock in Group4.

CREATE TABLE encounter (
    id text PRIMARY KEY CHECK (id ~ '^[A-Za-z0-9.-]{1,64}$'),
    patient_id text NOT NULL REFERENCES patient(id),
    class text NOT NULL CHECK (length(class) BETWEEN 1 AND 64),
    practitioner_id text NOT NULL REFERENCES actor(id),
    UNIQUE (id, patient_id)
);

CREATE TABLE proxy_link (
    patient_id text NOT NULL REFERENCES patient(id),
    proxy_id text NOT NULL REFERENCES actor(id),
    active boolean NOT NULL DEFAULT true,
    revoked_at timestamptz,
    PRIMARY KEY (patient_id, proxy_id),
    CHECK ((active AND revoked_at IS NULL) OR (NOT active AND revoked_at IS NOT NULL))
);

CREATE TABLE observation (
    id text PRIMARY KEY CHECK (id ~ '^[A-Za-z0-9.-]{1,64}$'),
    patient_id text NOT NULL REFERENCES patient(id),
    encounter_id text,
    code text NOT NULL CHECK (length(code) BETWEEN 1 AND 64),
    unit_code text NOT NULL CHECK (length(unit_code) BETWEEN 1 AND 64),
    value_num numeric,
    effective timestamptz NOT NULL,
    status text NOT NULL CHECK (status IN ('registered', 'preliminary', 'final', 'amended',
        'corrected', 'cancelled', 'entered-in-error', 'unknown')),
    correction_of text REFERENCES observation(id),
    component jsonb,
    birth_date_context date,
    sex_context text CHECK (sex_context IN ('male', 'female', 'unknown')),
    context_provenance jsonb NOT NULL DEFAULT '{}'::jsonb,
    standardized_creatinine boolean NOT NULL DEFAULT false,
    source_critical boolean NOT NULL DEFAULT false,
    FOREIGN KEY (encounter_id, patient_id) REFERENCES encounter(id, patient_id),
    CHECK (correction_of IS NULL OR correction_of <> id),
    CHECK (value_num IS NULL OR value_num NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)),
    CHECK (component IS NULL OR jsonb_typeof(component) = 'array'),
    CHECK (jsonb_typeof(context_provenance) = 'object'),
    UNIQUE (id, patient_id)
);

CREATE TABLE access_log (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id text CHECK (actor_id IS NULL OR length(actor_id) <= 64),
    actor_role text CHECK (actor_role IS NULL OR actor_role IN ('PATIENT', 'PROXY', 'CLINICIAN', 'STAFF')),
    patient_id text CHECK (patient_id IS NULL OR length(patient_id) <= 64),
    action text NOT NULL CHECK (action ~ '^[A-Z_]{1,40}$'),
    outcome text NOT NULL CHECK (outcome IN ('ALLOWED', 'DENIED')),
    source text NOT NULL CHECK (source IN ('HTTP', 'LOCAL_COMMAND', 'DATASET')),
    at timestamptz NOT NULL DEFAULT clock_timestamp()
);
-- Deliberately no audit actor/patient FK: unknown/missing targets and unverified actors need denials.

CREATE INDEX encounter_entitlement_idx ON encounter (patient_id, practitioner_id);
CREATE INDEX proxy_actor_idx ON proxy_link (proxy_id, patient_id) WHERE active;
CREATE INDEX observation_patient_time_idx ON observation (patient_id, effective DESC, id);
CREATE INDEX observation_patient_code_time_idx ON observation (patient_id, code, effective, id);
CREATE UNIQUE INDEX observation_one_successor_idx ON observation (correction_of) WHERE correction_of IS NOT NULL;
CREATE INDEX access_log_patient_time_idx ON access_log (patient_id, at DESC, id DESC);

REVOKE ALL ON ALL TABLES IN SCHEMA atlas FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA atlas FROM PUBLIC;
GRANT USAGE ON SCHEMA atlas TO atlas_runtime;
GRANT SELECT ON actor, patient_sex_context, proxy_link TO atlas_runtime;
GRANT SELECT, INSERT ON patient, patient_sex_context, encounter, observation, access_log TO atlas_runtime;
GRANT UPDATE (active, revoked_at) ON proxy_link TO atlas_runtime;
GRANT USAGE ON SEQUENCE access_log_id_seq TO atlas_runtime;
-- No clinical-record/audit UPDATE/DELETE, DDL or access to migration history for the runtime role.
