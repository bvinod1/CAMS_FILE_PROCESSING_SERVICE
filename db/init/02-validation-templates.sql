-- CAMS File Processing Service — Epic 3: Header Validation Pipeline
-- PostgreSQL schema for validation templates, results, and errors.
-- Applied automatically after 01-schema.sql when PostgreSQL container starts.

-- ============================================================
-- validation_templates
-- ============================================================

CREATE TABLE IF NOT EXISTS validation_templates (
    template_id     VARCHAR(36)     PRIMARY KEY,
    flow_type       VARCHAR(64)     NOT NULL,
    version         INTEGER         NOT NULL DEFAULT 1,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_template_flow_version UNIQUE (flow_type, version)
);

CREATE INDEX IF NOT EXISTS idx_vt_flow_type_active ON validation_templates (flow_type, active);

-- ============================================================
-- validation_column_rules
-- ============================================================

CREATE TABLE IF NOT EXISTS validation_column_rules (
    rule_id         VARCHAR(36)     PRIMARY KEY,
    template_id     VARCHAR(36)     NOT NULL REFERENCES validation_templates(template_id) ON DELETE CASCADE,
    column_name     VARCHAR(256)    NOT NULL,
    position        INTEGER         NOT NULL,
    data_type       VARCHAR(20)     NOT NULL DEFAULT 'STRING',
    required        BOOLEAN         NOT NULL DEFAULT TRUE,
    allow_null      BOOLEAN         NOT NULL DEFAULT FALSE,
    max_length      INTEGER,
    pattern         TEXT,

    CONSTRAINT chk_data_type CHECK (data_type IN ('STRING','INTEGER','DECIMAL','DATE','UUID')),
    CONSTRAINT uq_rule_template_pos UNIQUE (template_id, position)
);

CREATE INDEX IF NOT EXISTS idx_vcr_template_id ON validation_column_rules (template_id);

-- ============================================================
-- validation_results  (INSERT-only)
-- ============================================================

CREATE TABLE IF NOT EXISTS validation_results (
    result_id           VARCHAR(36)     PRIMARY KEY,
    file_id             VARCHAR(36)     NOT NULL REFERENCES file_records(file_id),
    flow_type           VARCHAR(64)     NOT NULL,
    template_id         VARCHAR(36)     NOT NULL REFERENCES validation_templates(template_id),
    template_version    INTEGER         NOT NULL,
    status              VARCHAR(30)     NOT NULL,
    error_count         INTEGER         NOT NULL DEFAULT 0,
    duration_ms         BIGINT,
    validated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_validation_status CHECK (status IN ('PASS', 'FAIL', 'UNPARSEABLE_HEADER'))
);

CREATE INDEX IF NOT EXISTS idx_vr_file_id ON validation_results (file_id);

-- ============================================================
-- validation_errors  (INSERT-only — never update or delete)
-- ============================================================

CREATE TABLE IF NOT EXISTS validation_errors (
    error_id        VARCHAR(36)     PRIMARY KEY,
    result_id       VARCHAR(36)     NOT NULL REFERENCES validation_results(result_id),
    column_name     VARCHAR(256),
    position        INTEGER         NOT NULL DEFAULT -1,
    error_code      VARCHAR(30)     NOT NULL,
    detail          TEXT,

    CONSTRAINT chk_error_code CHECK (error_code IN (
        'MISSING_COLUMN', 'WRONG_POSITION', 'EXTRA_COLUMN',
        'UNPARSEABLE_HEADER', 'NULL_NOT_ALLOWED', 'INVALID_DATA_TYPE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_ve_result_id ON validation_errors (result_id);

-- ============================================================
-- Security grants (local PostgreSQL user: cams_app_user)
-- ============================================================

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'cams_app_user') THEN
        GRANT INSERT, SELECT ON validation_errors TO cams_app_user;
        REVOKE UPDATE, DELETE ON validation_errors FROM cams_app_user;
        GRANT INSERT, SELECT ON validation_results TO cams_app_user;
        REVOKE UPDATE, DELETE ON validation_results FROM cams_app_user;
        GRANT INSERT, SELECT, UPDATE ON validation_templates TO cams_app_user;
        GRANT INSERT, SELECT, UPDATE ON validation_column_rules TO cams_app_user;
    END IF;
END $$;

-- ============================================================
-- Seed data — NAV flow type (local profile)
-- ============================================================

INSERT INTO validation_templates (template_id, flow_type, version, active)
    VALUES ('tmpl-nav-v1', 'NAV', 1, TRUE)
    ON CONFLICT DO NOTHING;

INSERT INTO validation_column_rules (rule_id, template_id, column_name, position, data_type, required, allow_null)
VALUES
    ('rule-nav-01', 'tmpl-nav-v1', 'FUND_CODE',       0, 'STRING',  TRUE, FALSE),
    ('rule-nav-02', 'tmpl-nav-v1', 'ISIN',             1, 'STRING',  TRUE, FALSE),
    ('rule-nav-03', 'tmpl-nav-v1', 'NAV_DATE',         2, 'DATE',    TRUE, FALSE),
    ('rule-nav-04', 'tmpl-nav-v1', 'NAV_VALUE',        3, 'DECIMAL', TRUE, FALSE),
    ('rule-nav-05', 'tmpl-nav-v1', 'CURRENCY',         4, 'STRING',  TRUE, FALSE),
    ('rule-nav-06', 'tmpl-nav-v1', 'SOURCE_SYSTEM',    5, 'STRING',  TRUE, FALSE)
ON CONFLICT DO NOTHING;

-- ============================================================
-- Seed data — TRANSACTION flow type (local profile)
-- ============================================================

INSERT INTO validation_templates (template_id, flow_type, version, active)
    VALUES ('tmpl-txn-v1', 'TRANSACTION', 1, TRUE)
    ON CONFLICT DO NOTHING;

INSERT INTO validation_column_rules (rule_id, template_id, column_name, position, data_type, required, allow_null)
VALUES
    ('rule-txn-01', 'tmpl-txn-v1', 'TRANSACTION_ID',   0, 'UUID',    TRUE, FALSE),
    ('rule-txn-02', 'tmpl-txn-v1', 'ACCOUNT_NO',       1, 'STRING',  TRUE, FALSE),
    ('rule-txn-03', 'tmpl-txn-v1', 'TXN_DATE',         2, 'DATE',    TRUE, FALSE),
    ('rule-txn-04', 'tmpl-txn-v1', 'TXN_TYPE',         3, 'STRING',  TRUE, FALSE),
    ('rule-txn-05', 'tmpl-txn-v1', 'AMOUNT',           4, 'DECIMAL', TRUE, FALSE),
    ('rule-txn-06', 'tmpl-txn-v1', 'CURRENCY',         5, 'STRING',  TRUE, FALSE),
    ('rule-txn-07', 'tmpl-txn-v1', 'STATUS',           6, 'STRING',  TRUE, FALSE),
    ('rule-txn-08', 'tmpl-txn-v1', 'SOURCE_SYSTEM',    7, 'STRING',  TRUE, FALSE)
ON CONFLICT DO NOTHING;
