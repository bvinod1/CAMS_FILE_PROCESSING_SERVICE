# Data Model — Epic 4: Chunked Record Processing

## New Tables

### `processing_jobs`

One row per Spring Batch job execution for a file. Tracks the overall batch state.

```sql
CREATE TABLE processing_jobs (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    file_id             UUID            NOT NULL REFERENCES file_records(id),
    batch_job_instance_id BIGINT,      -- Spring Batch BATCH_JOB_INSTANCE.JOB_INSTANCE_ID
    batch_job_execution_id BIGINT,     -- Spring Batch BATCH_JOB_EXECUTION.JOB_EXECUTION_ID
    chunk_size          INTEGER         NOT NULL,
    thread_pool_size    INTEGER         NOT NULL,
    total_records       INTEGER,
    synced_count        INTEGER         NOT NULL DEFAULT 0,
    failed_count        INTEGER         NOT NULL DEFAULT 0,
    skipped_count       INTEGER         NOT NULL DEFAULT 0,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    -- PENDING | RUNNING | COMPLETED | PARTIALLY_COMPLETED | FAILED
    CONSTRAINT pk_processing_jobs PRIMARY KEY (id),
    CONSTRAINT fk_processing_jobs_file FOREIGN KEY (file_id) REFERENCES file_records(id)
);

CREATE UNIQUE INDEX idx_processing_jobs_file_id ON processing_jobs (file_id);
CREATE INDEX idx_processing_jobs_status ON processing_jobs (status);
```

---

### `record_results`

One row per processed record. INSERT-only — status is written once and never updated.

```sql
CREATE TABLE record_results (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    processing_job_id   UUID            NOT NULL REFERENCES processing_jobs(id),
    file_id             UUID            NOT NULL REFERENCES file_records(id),
    row_number          INTEGER         NOT NULL,
    status              VARCHAR(20)     NOT NULL,   -- SYNCED | FAILED | SKIPPED
    retry_count         INTEGER         NOT NULL DEFAULT 0,
    request_payload     JSONB,          -- PII-redacted copy of the API request
    response_code       VARCHAR(10),
    error_code          VARCHAR(50),
    error_message       TEXT,
    processed_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    duration_ms         INTEGER,
    CONSTRAINT pk_record_results PRIMARY KEY (id),
    CONSTRAINT chk_record_results_status CHECK (status IN ('SYNCED', 'FAILED', 'SKIPPED'))
);

-- INSERT-only enforcement
REVOKE UPDATE, DELETE ON record_results FROM cams_app;

CREATE INDEX idx_record_results_processing_job_id ON record_results (processing_job_id);
CREATE INDEX idx_record_results_file_id ON record_results (file_id);
CREATE INDEX idx_record_results_status ON record_results (status);
CREATE INDEX idx_record_results_row_number ON record_results (processing_job_id, row_number);
```

---

## Events

### `ProcessingCompletedEvent`

Published to the `cams.processing.completed` topic on batch job completion.

```json
{
  "eventId":          "uuid",
  "fileId":           "uuid",
  "flowType":         "TRANSACTION",
  "processingJobId":  "uuid",
  "result":           "COMPLETED | PARTIALLY_COMPLETED | FAILED",
  "totalRecords":     10000,
  "syncedCount":      9995,
  "failedCount":      5,
  "skippedCount":     0,
  "durationMs":       45230,
  "occurredAt":       "2024-01-15T12:00:00Z"
}
```

### `ProcessingFailedEvent` (DLQ)

Published to `cams.dlq` when a file fails processing entirely.

```json
{
  "eventId":          "uuid",
  "fileId":           "uuid",
  "processingJobId":  "uuid",
  "failedAt":         "PROCESSING",
  "lastErrorMessage": "Circuit breaker OPEN — downstream API unavailable",
  "retryEligible":    true,
  "occurredAt":       "2024-01-15T12:05:00Z"
}
```

---

## Retry / Circuit Breaker Config (stored in `processing_config`, see Epic 7)

| Parameter | Default | Description |
|---|---|---|
| `retryMaxAttempts` | 3 | Max retries per record |
| `retryBackoffMs` | [1000, 2000, 4000] | Backoff intervals for retries |
| `circuitBreakerThreshold` | 5 | Consecutive failures before CB opens |
| `circuitBreakerWindowMs` | 30000 | Sliding window for failure counting |
| `circuitBreakerCooldownMs` | 60000 | Wait time before CB half-opens |

---

## Status Transitions (Epic 4 scope)

```
VALIDATED → PROCESSING → COMPLETED
                       → PARTIALLY_COMPLETED
                       → FAILED
```

---

## Spring Batch Schema

Spring Batch requires its own schema tables (`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION`, etc.). These are created by Spring Batch automatically via `spring.batch.jdbc.initialize-schema=always` in the local profile.

The `processing_jobs.batch_job_execution_id` foreign reference links the CAMS job record to the Spring Batch audit tables.

---

## IAM / Access Control

| Role | Tables | Access |
|---|---|---|
| `cams_app` | `processing_jobs` | SELECT, INSERT, UPDATE |
| `cams_app` | `record_results` | SELECT, INSERT only (no UPDATE/DELETE) |
| `cams_readonly` | all | SELECT only |
