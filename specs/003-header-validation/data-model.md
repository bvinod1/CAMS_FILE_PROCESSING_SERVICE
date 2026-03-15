# Data Model — Epic 3: Header Validation

## New Tables

### `validation_templates`

Stores the column validation rules for each flow type. Rules are stored as a JSON array enabling dynamic onboarding without schema changes.

```sql
CREATE TABLE validation_templates (
    flow_type           VARCHAR(50)     NOT NULL,
    version             INTEGER         NOT NULL DEFAULT 1,
    column_rules        JSONB           NOT NULL,  -- see Column Rule schema below
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          VARCHAR(100)    NOT NULL,
    updated_by          VARCHAR(100)    NOT NULL,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_validation_templates PRIMARY KEY (flow_type, version)
);

-- Active template index
CREATE UNIQUE INDEX idx_validation_templates_active
    ON validation_templates (flow_type)
    WHERE active = TRUE;
```

**Column Rules JSON Schema:**
```json
{
  "columns": [
    {
      "name":        "ACCOUNT_ID",
      "position":    0,
      "dataType":    "UUID",
      "required":    true,
      "allowNull":   false,
      "maxLength":   36,
      "pattern":     null
    },
    {
      "name":        "NAV_DATE",
      "position":    1,
      "dataType":    "DATE",
      "required":    true,
      "allowNull":   false,
      "maxLength":   null,
      "pattern":     "^\\d{4}-\\d{2}-\\d{2}$"
    }
  ]
}
```

**Supported `dataType` values:** `STRING`, `INTEGER`, `DECIMAL`, `DATE`, `UUID`, `BOOLEAN`

---

### `validation_results`

One row per validated file, recording the outcome and the template version used.

```sql
CREATE TABLE validation_results (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    file_id             UUID            NOT NULL REFERENCES file_records(id),
    flow_type           VARCHAR(50)     NOT NULL,
    template_version    INTEGER         NOT NULL,
    result              VARCHAR(20)     NOT NULL,   -- PASS | FAIL | UNPARSEABLE_HEADER
    error_count         INTEGER         NOT NULL DEFAULT 0,
    validated_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    duration_ms         INTEGER,
    CONSTRAINT pk_validation_results PRIMARY KEY (id),
    CONSTRAINT fk_validation_results_file FOREIGN KEY (file_id) REFERENCES file_records(id)
);

CREATE INDEX idx_validation_results_file_id ON validation_results (file_id);
```

---

### `validation_errors`

One row per column violation in a failed file. INSERT-only — errors are never modified.

```sql
CREATE TABLE validation_errors (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    validation_id       UUID            NOT NULL REFERENCES validation_results(id),
    file_id             UUID            NOT NULL REFERENCES file_records(id),
    row_number          INTEGER         NOT NULL,   -- 0 = header row
    column_name         VARCHAR(100)    NOT NULL,
    column_position     INTEGER,
    error_code          VARCHAR(50)     NOT NULL,   -- MISSING_COLUMN | WRONG_POSITION | INVALID_TYPE | etc.
    error_message       TEXT            NOT NULL,
    CONSTRAINT pk_validation_errors PRIMARY KEY (id)
);

-- INSERT-only enforcement
REVOKE UPDATE, DELETE ON validation_errors FROM cams_app;

CREATE INDEX idx_validation_errors_file_id ON validation_errors (file_id);
CREATE INDEX idx_validation_errors_validation_id ON validation_errors (validation_id);
```

**Error Codes:**

| Code | Meaning |
|---|---|
| `MISSING_COLUMN` | Required column not found in header row |
| `WRONG_POSITION` | Column found but at the wrong index |
| `EXTRA_COLUMN` | Unexpected column present (if strict mode enabled for template) |
| `INVALID_DATA_TYPE` | Column header value could not be cast to expected type |
| `UNPARSEABLE_HEADER` | First row of file could not be read (encoding error, binary file) |
| `NULL_NOT_ALLOWED` | Column marked `allowNull=false` but value is empty |

---

## Events

### `ValidationCompletedEvent`

Published to the `cams.validation.completed` topic after every validation outcome.

```json
{
  "eventId":         "uuid",
  "fileId":          "uuid",
  "flowType":        "NAV",
  "templateVersion": 3,
  "result":          "PASS | FAIL | UNPARSEABLE_HEADER",
  "errorCount":      0,
  "durationMs":      142,
  "occurredAt":      "2024-01-15T10:30:00Z"
}
```

---

## Cache Structure

| Cache Name | Key | Value | TTL |
|---|---|---|---|
| `validationTemplateCache` | `flowType` | `ValidationTemplate` POJO | 60 seconds |

---

## Status Transitions (Epic 3 scope)

```
SCANNED_CLEAN → VALIDATING → VALIDATED
                           → VALIDATION_FAILED
```

---

## IAM / Access Control

| Role | Tables | Access |
|---|---|---|
| `cams_app` | `validation_templates` | SELECT, INSERT, UPDATE |
| `cams_app` | `validation_results` | SELECT, INSERT |
| `cams_app` | `validation_errors` | SELECT, INSERT only (no UPDATE/DELETE) |
| `cams_readonly` | all | SELECT only |
