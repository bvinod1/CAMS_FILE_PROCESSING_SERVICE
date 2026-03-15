# Epic 5: File Lifecycle State Machine

## Overview

The CAMS platform uses Spring State Machine to govern all `FileRecord` status transitions. The state machine is the single authoritative gatekeeper — no code path may transition a file's status without going through the machine. Every transition is persisted to the immutable `file_status_audit` table before the downstream event is published (transactional outbox pattern).

---

## User Stories

### US-501: Enforce All FileStatus Transitions via Spring State Machine

**As a** platform engineer,
**I want** all file status transitions to be validated and executed exclusively by the Spring State Machine,
**So that** invalid transitions (e.g. jumping from `AWAITING_UPLOAD` directly to `PROCESSING`) are impossible at the code level.

**Acceptance Criteria:**

- AC-01: A `FileStateMachine` bean is configured with all valid transitions as defined in `constitution.md §8`.
- AC-02: Any attempt to perform an invalid transition throws a `InvalidStateTransitionException` which is caught and routes the file to `FAILED` with an audit entry.
- AC-03: The machine's state is persisted to the `file_records.status` column via `SpannerStateMachinePersist` (GCP) / `JpaStateMachinePersist` (local) after every transition.
- AC-04: The state machine is stateless across requests — a `FileStateMachine` is recreated from the persisted state on each operation, not held in memory between events.

### US-502: Immutable Audit Trail for Every Transition

**As a** compliance officer,
**I want** every file status transition to be recorded permanently with the timestamp, actor, and reason,
**So that** a complete, tamper-proof history of every file's lifecycle is available for audit and regulatory reporting.

**Acceptance Criteria:**

- AC-01: Every state transition writes a row to `file_status_audit` (`from_status`, `to_status`, `actor`, `reason`, `transitioned_at`).
- AC-02: The `file_status_audit` table is INSERT-only — UPDATE and DELETE are revoked at the database level.
- AC-03: Audit records are retained for at least 7 years (enforced by retention policy, not application logic).
- AC-04: `GET /api/v1/uploads/{fileId}/history` returns the full ordered audit trail for a file.

### US-503: Dead-Letter Queue Routing and Manual Retry

**As a** platform operator,
**I want** files that reach a terminal failure state to be automatically routed to a dead-letter queue with full context,
**So that** operations can investigate, correct the root cause, and manually retry the file without data loss.

**Acceptance Criteria:**

- AC-01: When a file transitions to `FAILED`, `QUARANTINED`, `SCAN_ERROR`, or `VALIDATION_FAILED`, a `FileFailedEvent` is published to the DLQ exchange/topic.
- AC-02: `GET /api/v1/admin/dlq` lists all files currently in a terminal failure state with their last error message.
- AC-03: `POST /api/v1/admin/dlq/{fileId}/retry` resets the file to the last stable state that enables a retry (e.g. `UPLOADED` for scan errors, `SCANNED_CLEAN` for validation failures) and re-publishes the appropriate trigger event.
- AC-04: Manual retry is audit-logged with `actor = "MANUAL_RETRY"` and the operator's identity.

### US-504: Priority Queue — P0 Files Never Starved

**As a** portfolio manager,
**I want** NAV files (P0 priority) to always be processed ahead of lower-priority files,
**So that** end-of-day NAV reporting SLAs are never missed due to a backlog of bulk files.

**Acceptance Criteria:**

- AC-01: The message queues (RabbitMQ / Pub/Sub) have separate queues per priority level: `cams.p0`, `cams.p1`, `cams.p2`.
- AC-02: Workers consume from `cams.p0` first; they only consume from `cams.p1` if `cams.p0` is empty.
- AC-03: A Prometheus gauge `cams_queue_depth{priority="P0"}` is published continuously.
- AC-04: An alert fires if a P0 file has been in `SCANNING`, `VALIDATING`, or `PROCESSING` state for more than 30 minutes.

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-501 | The `FileStateMachine` must be configured using `StateMachineConfigurerAdapter` — not ad-hoc `setStatus()` calls. |
| FR-502 | State machine configuration must be the single source of truth for valid transitions — the `FileStatus` enum defines valid states; the machine defines valid edges. |
| FR-503 | Every transition action must be idempotent — receiving the same event twice must produce the same result. |
| FR-504 | The audit record INSERT and the `file_records.status` UPDATE must be committed in the same transaction. |
| FR-505 | DLQ messages must include: `fileId`, `fromStatus`, `toStatus`, `errorMessage`, `actor`, `occurredAt`, and the full audit trail length. |

---

## Non-Functional Requirements

| ID | Requirement | Target |
|---|---|---|
| NFR-501 | State transition latency (p99) | < 100 ms (DB write + event publish) |
| NFR-502 | Audit trail query latency (p99) | < 200 ms for files with up to 50 transitions |
| NFR-503 | DLQ depth alert threshold | P0 queue depth > 0 for > 5 minutes |
| NFR-504 | Retry safety | Manual retry is idempotent — retrying the same file twice is safe |
