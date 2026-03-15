# Data Model — Epic 5: File Lifecycle State Machine

## State Transition Diagram

```
                          ┌─────────────────────────────────────────────────────────┐
                          │                   TERMINAL STATES                        │
                          │  QUARANTINED  FAILED  SCAN_ERROR  VALIDATION_FAILED      │
                          └─────────────────────────────────────────────────────────┘
                                  ▲         ▲         ▲              ▲
                                  │         │         │              │
PENDING_UPLOAD → AWAITING_UPLOAD → UPLOADED → SCANNING ──────────────┤
                                               │                     │
                                               ▼                     │
                                          SCANNED_CLEAN              │
                                               │                     │
                                               ▼                     │
                                          VALIDATING ────────────────┘
                                               │
                                               ▼
                                          VALIDATED
                                               │
                                               ▼
                                          PROCESSING
                                               │
                              ┌────────────────┴──────────────────┐
                              ▼                                    ▼
                          COMPLETED                      PARTIALLY_COMPLETED
```

---

## Valid Transitions Table

| From State | To State | Trigger Event | Actor |
|---|---|---|---|
| `PENDING_UPLOAD` | `AWAITING_UPLOAD` | Upload URL created | System |
| `AWAITING_UPLOAD` | `UPLOADED` | Upload confirmed (`POST /{fileId}/confirm`) | User |
| `UPLOADED` | `SCANNING` | `FileReceivedEvent` consumed by Scan Worker | Worker |
| `SCANNING` | `SCANNED_CLEAN` | ClamAV returns CLEAN | Worker |
| `SCANNING` | `QUARANTINED` | ClamAV returns INFECTED | Worker |
| `SCANNING` | `SCAN_ERROR` | ClamAV unreachable / timeout | Worker |
| `SCANNED_CLEAN` | `VALIDATING` | `ScanCompletedEvent(CLEAN)` consumed by Validation Worker | Worker |
| `VALIDATING` | `VALIDATED` | Header validation passes | Worker |
| `VALIDATING` | `VALIDATION_FAILED` | Header validation fails | Worker |
| `VALIDATED` | `PROCESSING` | `ValidationCompletedEvent(PASS)` consumed by Processing Worker | Worker |
| `PROCESSING` | `COMPLETED` | All records synced | Worker |
| `PROCESSING` | `PARTIALLY_COMPLETED` | Some records failed after max retries | Worker |
| `PROCESSING` | `FAILED` | Circuit breaker open + job abandoned | Worker |
| Any non-terminal | `FAILED` | Unhandled exception in any worker | System |

---

## Updated `file_records` Schema

The `status` column constraint is updated to include all 13 states:

```sql
ALTER TABLE file_records
    DROP CONSTRAINT chk_file_status;

ALTER TABLE file_records
    ADD CONSTRAINT chk_file_status CHECK (status IN (
        'PENDING_UPLOAD', 'AWAITING_UPLOAD', 'UPLOADED',
        'SCANNING', 'SCANNED_CLEAN', 'QUARANTINED', 'SCAN_ERROR',
        'VALIDATING', 'VALIDATED', 'VALIDATION_FAILED',
        'PROCESSING', 'COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED'
    ));
```

---

## `file_status_audit` Table

Already created in Epic 1. Reminder of schema for completeness:

```sql
CREATE TABLE file_status_audit (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    file_id         UUID            NOT NULL REFERENCES file_records(id),
    from_status     VARCHAR(30)     NOT NULL,
    to_status       VARCHAR(30)     NOT NULL,
    actor           VARCHAR(100)    NOT NULL,
    reason          TEXT,
    transitioned_at TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT pk_file_status_audit PRIMARY KEY (id)
);

-- INSERT-only
REVOKE UPDATE, DELETE ON file_status_audit FROM cams_app;

CREATE INDEX idx_file_status_audit_file_id ON file_status_audit (file_id);
CREATE INDEX idx_file_status_audit_transitioned_at ON file_status_audit (transitioned_at DESC);
```

---

## DLQ Message Schema

Published to `cams.dlq` topic when a file enters a terminal failure state.

```json
{
  "eventId":          "uuid",
  "fileId":           "uuid",
  "fileName":         "nav_20240115.csv",
  "flowType":         "NAV",
  "priority":         "P0",
  "failedAtStatus":   "SCAN_ERROR",
  "lastErrorMessage": "ClamAV connection timeout after 30000ms",
  "retryEligibleFromStatus": "UPLOADED",
  "auditTrailLength": 4,
  "occurredAt":       "2024-01-15T10:30:00Z"
}
```

---

## Priority Queue Routing

| Priority | Queue Name (RabbitMQ) | Topic Name (Pub/Sub) | SLA |
|---|---|---|---|
| P0 | `cams.p0` | `cams-events-p0` | 30 minutes end-to-end |
| P1 | `cams.p1` | `cams-events-p1` | 4 hours end-to-end |
| P2 | `cams.p2` | `cams-events-p2` | 24 hours end-to-end |

Worker consumption order: `cams.p0` → `cams.p1` → `cams.p2` (strict priority).

---

## Spring State Machine — State and Event Enums

```java
// States map 1:1 to FileStatus enum (in common/FileStatus.java)
public enum FileState {
    PENDING_UPLOAD, AWAITING_UPLOAD, UPLOADED,
    SCANNING, SCANNED_CLEAN, QUARANTINED, SCAN_ERROR,
    VALIDATING, VALIDATED, VALIDATION_FAILED,
    PROCESSING, COMPLETED, PARTIALLY_COMPLETED, FAILED
}

public enum FileEvent {
    CREATE_UPLOAD_URL,
    CONFIRM_UPLOAD,
    BEGIN_SCAN,
    SCAN_CLEAN,
    SCAN_INFECTED,
    SCAN_ERROR,
    BEGIN_VALIDATION,
    VALIDATION_PASS,
    VALIDATION_FAIL,
    BEGIN_PROCESSING,
    PROCESSING_COMPLETE,
    PROCESSING_PARTIAL,
    PROCESSING_FAIL,
    SYSTEM_ERROR
}
```
