# Epic 4: Chunked Record Processing

## Overview

After a file is validated, its records must be processed in configurable chunks using Spring Batch. Each record is sent to an external synchronous API. The system must handle partial failures gracefully, track per-record status, support retry with exponential backoff, and report both file-level and record-level completion status to consumers.

---

## User Stories

### US-401: Process Records in Configurable Chunks

**As a** platform engineer,
**I want** validated file records to be processed in chunks using Spring Batch,
**So that** 1M+ record files are handled within the 2-hour SLA without memory exhaustion.

**Acceptance Criteria:**

- AC-01: When a `ValidationCompletedEvent(result=PASS)` is received, the Processing Worker transitions `FileRecord.status` to `PROCESSING` and starts a Spring Batch job.
- AC-02: The Batch job reads the file in chunks (default chunk size: 500 records, configurable per `flowType`).
- AC-03: For each record, the processor calls the external synchronous API with a timeout of 5 seconds.
- AC-04: A `record_results` row is persisted for every record, with status `SYNCED`, `FAILED`, or `SKIPPED`.
- AC-05: On completion, if all records are `SYNCED`, `FileRecord.status` → `COMPLETED`. If any records are `FAILED`, → `PARTIALLY_COMPLETED`. A `ProcessingCompletedEvent` is published in both cases.

### US-402: Handle External API Failures with Retry and Circuit Breaker

**As a** platform engineer,
**I want** transient external API failures to be retried with exponential backoff, and persistent failures to open a circuit breaker,
**So that** a temporarily unavailable downstream system does not cascade failures into the processing pipeline.

**Acceptance Criteria:**

- AC-01: Failed external API calls are retried up to 3 times with exponential backoff (1s, 2s, 4s).
- AC-02: After 5 consecutive failures within a 30-second window, the circuit breaker opens and the batch job pauses for a configurable cool-down period (default 60 seconds).
- AC-03: When the circuit breaker opens, an alert is raised and `FileRecord.status` → `PROCESSING` remains (not failed) — the job resumes when the circuit closes.
- AC-04: Records that exhaust all retries are marked `FAILED` in `record_results` with the error code and message from the last attempt.

### US-403: Per-Record Status Tracking

**As a** portfolio manager,
**I want** to see which specific records in my file succeeded, failed, or were skipped,
**So that** I can investigate failures and resubmit only the affected records.

**Acceptance Criteria:**

- AC-01: `GET /api/v1/uploads/{fileId}/records` returns a paginated list of record results with status, error details, and retry count.
- AC-02: `GET /api/v1/uploads/{fileId}/records?status=FAILED` filters to only failed records.
- AC-03: Failed records include the row number, the request payload sent to the external API (redacted PII), and the error response received.

### US-404: Local Development Profile

**As a** developer,
**I want** the external API to be stubbed by WireMock locally,
**So that** I can test processing without calling the real downstream system.

**Acceptance Criteria:**

- AC-01: WireMock is pre-configured with a success mapping for standard records and an error mapping for records where a specific field equals `"ERROR_TEST"`.
- AC-02: The batch job runs end-to-end against WireMock with a small test file (10 records).
- AC-03: Circuit breaker behaviour can be tested by activating the WireMock error scenario via `POST /wiremock/__admin/scenarios`.

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-401 | Spring Batch job must use partitioned processing — one partition per chunk, enabling parallel processing across thread pool workers. |
| FR-402 | Chunk size must be configurable per `flowType` via the `validation_templates` table (Epic 3 / Epic 7). |
| FR-403 | The external API client must be based on `ExternalApiPort` interface — WireMock adapter for `local`, real HTTP adapter for `gcp`. |
| FR-404 | PII fields (configurable per template) must be replaced with `[REDACTED]` before logging the request payload. |
| FR-405 | Batch job state (step execution, chunk offsets) must be persisted in the database so a crashed job can resume from the last committed chunk. |
| FR-406 | A DLQ message is published when a file reaches `PARTIALLY_COMPLETED` — allowing downstream alerting systems to trigger human review. |

---

## Non-Functional Requirements

| ID | Requirement | Target |
|---|---|---|
| NFR-401 | Processing throughput (1M record file) | Completed in < 2 hours (p95) |
| NFR-402 | External API call latency (p99) | < 5 seconds per record |
| NFR-403 | Batch thread pool size | Configurable; default 4 threads |
| NFR-404 | Record status write latency | `record_results` persisted within the same transaction as each chunk commit |
| NFR-405 | Memory per instance | No more than 500 records held in memory at any time (enforced by chunk size) |
