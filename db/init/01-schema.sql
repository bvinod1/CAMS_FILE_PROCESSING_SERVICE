-- CAMS File Processing Service — PostgreSQL schema initialisation
-- Applied automatically when PostgreSQL container starts (docker-compose).
-- Keep in sync with the Spanner schema used in GCP environments.

CREATE TABLE IF NOT EXISTS file_records (
    file_id             VARCHAR(36)     PRIMARY KEY,
    original_file_name  VARCHAR(512)    NOT NULL,
    flow_type           VARCHAR(64)     NOT NULL,
    ingress_channel     VARCHAR(32)     NOT NULL DEFAULT 'REST',
    checksum_md5        CHAR(32),
    checksum_sha256     CHAR(64),
    checksum            VARCHAR(255)    NOT NULL,
    status              VARCHAR(32)     NOT NULL DEFAULT 'AWAITING_UPLOAD',
    priority            SMALLINT        NOT NULL DEFAULT 1,
    gcs_bucket          VARCHAR(256),
    gcs_object_path     VARCHAR(1024),
    scan_id             VARCHAR(36),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    scanned_at          TIMESTAMPTZ,
    validated_at        TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    error_message       TEXT,

    CONSTRAINT chk_file_status CHECK (status IN (
        'AWAITING_UPLOAD', 'UPLOADED', 'SCANNING', 'SCANNED_CLEAN',
        'QUARANTINED', 'SCAN_ERROR', 'VALIDATING', 'VALIDATED',
        'VALIDATION_FAILED', 'PROCESSING', 'COMPLETED',
        'PARTIALLY_COMPLETED', 'FAILED'
    ))
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

-- ============================================================
-- Epic 2: Malware Scanning
-- ============================================================

-- Immutable scan results — INSERT only, UPDATE/DELETE revoked at application user level
CREATE TABLE IF NOT EXISTS scan_results (
    scan_id             VARCHAR(36)     PRIMARY KEY,
    file_id             VARCHAR(36)     NOT NULL REFERENCES file_records(file_id),
    result              VARCHAR(20)     NOT NULL,
    virus_name          VARCHAR(256),
    engine_version      VARCHAR(50)     NOT NULL,
    signature_date      DATE            NOT NULL,
    error_detail        TEXT,
    scanned_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    scan_duration_ms    BIGINT,

    CONSTRAINT chk_scan_result CHECK (result IN ('CLEAN', 'INFECTED', 'ERROR'))
);

CREATE INDEX IF NOT EXISTS idx_scan_results_file_id ON scan_results (file_id);

