# Epic 3: Header Validation Pipeline

## Overview

Before records in a file can be processed, the file's structure must be validated against a configurable template specific to its `flowType`. Validation checks that required columns exist, are in the correct order, have the correct data types, and meet business rules. Only files that pass validation proceed to record processing; failed files are routed to a dead-letter queue with field-level error reports.

---

## User Stories

### US-301: Validate File Headers Against Dynamic Templates

**As a** data operations manager,
**I want** every uploaded file to be automatically validated against the expected column structure for its flow type,
**So that** malformed files are rejected immediately with actionable error reports before any records are persisted.

**Acceptance Criteria:**

- AC-01: When a `ScanCompletedEvent(result=CLEAN)` is received, the Validation Worker transitions `FileRecord.status` to `VALIDATING`.
- AC-02: The worker reads the first row (header row) of the file from the processing bucket and compares it against the `validation_templates` record for the matching `flowType`.
- AC-03: If all required columns are present, in the correct order, the status transitions to `VALIDATED` and a `ValidationCompletedEvent(result=PASS)` is published.
- AC-04: If validation fails, the status transitions to `VALIDATION_FAILED`, a `ValidationCompletedEvent(result=FAIL)` is published, and a `validation_errors` report is persisted with row number, column name, and reason for each failure.
- AC-05: Validation errors are available via `GET /api/v1/uploads/{fileId}/validation-errors`.

### US-302: Dynamic Validation Template Management

**As a** platform engineer,
**I want** to create and update validation templates for a given flow type via an API without redeploying the service,
**So that** new file formats can be onboarded in minutes rather than requiring a release cycle.

**Acceptance Criteria:**

- AC-01: `POST /api/v1/validation-templates` creates a new template for a `flowType`.
- AC-02: `PUT /api/v1/validation-templates/{flowType}` updates the column rules for an existing template.
- AC-03: A template change takes effect immediately for all subsequent validation jobs (no restart required).
- AC-04: Template versions are tracked — each update increments a version number; the version used for each file's validation is recorded in `validation_results`.
- AC-05: Deleting a template that is referenced by in-flight files is rejected with `409 Conflict`.

### US-303: Local Development Profile

**As a** developer,
**I want** validation templates to be seeded from SQL scripts on startup in the local profile,
**So that** I can validate files end-to-end without any cloud services.

**Acceptance Criteria:**

- AC-01: `db/init/02-validation-templates.sql` seeds templates for `NAV` and `TRANSACTION` flow types.
- AC-02: The validation worker uses the PostgreSQL template store when `spring.profiles.active=local`.
- AC-03: Templates can be modified at runtime via the API even in the local profile.

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-301 | Validation logic must only access templates via `ValidationTemplatePort` — never query the DB directly. |
| FR-302 | The header row is read as a stream from the first line of the file — the entire file must not be loaded into memory for header validation. |
| FR-303 | Validation rules per column: `required` (boolean), `position` (zero-indexed), `dataType` (`STRING`, `INTEGER`, `DECIMAL`, `DATE`, `UUID`), `maxLength` (optional), `allowNull` (boolean), `pattern` (optional regex). |
| FR-304 | `ValidationCompletedEvent` must be published only after `validation_results` is persisted (transactional outbox). |
| FR-305 | Files where the header row cannot be parsed (e.g. binary file, encoding error) are treated as `VALIDATION_FAILED` with reason `UNPARSEABLE_HEADER`. |
| FR-306 | Validation is non-blocking — the file read and template lookup run on `Schedulers.boundedElastic()`. |

---

## Non-Functional Requirements

| ID | Requirement | Target |
|---|---|---|
| NFR-301 | Validation throughput | ≥ 200 files/min per instance |
| NFR-302 | Validation latency (p95, reading first line) | < 2 seconds |
| NFR-303 | Template lookup latency (p99) | < 50 ms (cached in memory with 60s TTL) |
| NFR-304 | Error report completeness | All column violations reported in a single pass — not just the first error |
