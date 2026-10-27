-- Mutable workflow metadata is separate from immutable source observations/audit events.
GRANT UPDATE (criticality, reviewed_by, reviewed_at, critical_path_by,
    critical_path_at, released_by, released_at) ON observation_review TO atlas_runtime;

ALTER TABLE proxy_link ADD COLUMN revoked_by text REFERENCES actor(id);
ALTER TABLE proxy_link ADD CONSTRAINT proxy_revoker_requires_revoked_link
    CHECK (revoked_by IS NULL OR (NOT active AND revoked_at IS NOT NULL));
GRANT UPDATE (revoked_by) ON proxy_link TO atlas_runtime;

-- Keep unknown-target denials recordable without an audit foreign key.
ALTER TABLE access_log ADD COLUMN target_proxy_id text
    CHECK (target_proxy_id IS NULL OR target_proxy_id ~ '^[A-Za-z0-9._-]{1,64}$');
-- No source observation/audit UPDATE/DELETE is granted. No reset or reactivation command is provided.
