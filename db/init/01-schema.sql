-- CAMS File Processing Service — PostgreSQL schema initialisation
-- Applied automatically when PostgreSQL container starts (docker-compose).
-- Keep in sync with the Spanner schema used in GCP environments.

CREATE TABLE IF NOT EXISTS file_records (
    file_id             VARCHAR(36)     PRIMARY KEY,
    original_file_name  VARCHAR(512)    NOT NULL,
    flow_type           VARCHAR(64)     NOT NULL,
    ingress_channel     VARCHAR(32)     NOT NULL DEFAULT 'REST',
    checksum_md5        CHAR(32)        NOT NULL,
    checksum_sha256     CHAR(64),
    status              VARCHAR(32)     NOT NULL DEFAULT 'AWAITING_UPLOAD',
    priority            SMALLINT        NOT NULL DEFAULT 1,
    gcs_bucket          VARCHAR(256),
    gcs_object_path     VARCHAR(1024),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    scanned_at          TIMESTAMPTZ,
    validated_at        TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    error_message       TEXT
);

CREATE TABLE IF NOT EXISTS file_status_audit (
    audit_id            VARCHAR(36)     PRIMARY KEY,
    file_id             VARCHAR(36)     NOT NULL REFERENCES file_records(file_id),
    from_status         VARCHAR(32),
    to_status           VARCHAR(32)     NOT NULL,
    actor               VARCHAR(128)    NOT NULL,
    reason              TEXT,
    transitioned_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_file_records_status    ON file_records (status);
CREATE INDEX IF NOT EXISTS idx_file_records_flow_type ON file_records (flow_type);
CREATE INDEX IF NOT EXISTS idx_audit_file_id          ON file_status_audit (file_id);
