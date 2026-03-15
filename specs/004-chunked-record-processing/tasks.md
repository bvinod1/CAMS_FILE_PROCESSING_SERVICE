# Tasks — Epic 4: Chunked Record Processing

## T401 — Processing Domain Model

**Task:** Create processing-related domain objects and enums.

**Subtasks:**
- T401-A: Create `ProcessingJob` JPA entity (id, fileId, batchJobExecutionId, chunkSize, threadPoolSize, totalRecords, syncedCount, failedCount, skippedCount, startedAt, completedAt, status)
- T401-B: Create `RecordResult` JPA entity (id, processingJobId, fileId, rowNumber, status, retryCount, requestPayload (JSONB), responseCode, errorCode, errorMessage, processedAt, durationMs)
- T401-C: Create `RecordStatus` enum: `SYNCED`, `FAILED`, `SKIPPED`
- T401-D: Create `ProcessingJobStatus` enum: `PENDING`, `RUNNING`, `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`

**Acceptance:** Entities compile; Hibernate can create tables from entity model.

---

## T402 — Database Schema for Processing

**Task:** Add `processing_jobs` and `record_results` tables to the DB schema.

**Subtasks:**
- T402-A: Add `processing_jobs` and `record_results` DDL to `db/init/01-schema.sql`
- T402-B: Add INSERT-only grant on `record_results` (REVOKE UPDATE, DELETE FROM cams_app)
- T402-C: Add Spring Batch schema initialisation (`spring.batch.jdbc.initialize-schema=always` in `application-local.yml`)
- T402-D: Verify tables are created cleanly on `docker-compose up`

**Acceptance:** `docker-compose up` → all tables exist; `REVOKE` grants verified with `\dp record_results`.

---

## T403 — ExternalApiPort and WireMock Adapter

**Task:** Create the port interface for the external synchronous API and the local WireMock adapter.

**Subtasks:**
- T403-A: Create `ExternalApiPort` interface: `syncRecord(flowType, rowNumber, payload) → ExternalApiResponse`
- T403-B: Create `HttpExternalApiAdapter` (`@Profile("gcp")`) using Spring WebClient
- T403-C: Create `WireMockExternalApiAdapter` (`@Profile("local")`) using WireMock Java client
- T403-D: Add WireMock stub mappings: `200 OK` for standard records, `500` for records where a configurable field equals `"ERROR_TEST"`
- T403-E: Write `ExternalApiContractTest` (abstract base) with `LocalExternalApiContractTest` subclass

**Acceptance:** Contract test passes; WireMock stubs return correct responses.

---

## T404 — Spring Batch Job Configuration

**Task:** Configure the partitioned Spring Batch job for chunked record processing.

**Subtasks:**
- T404-A: Create `ProcessingBatchConfig` (`@Configuration`) defining `Job`, `Step`, `Partitioner`, `ItemReader`, `ItemProcessor`, `ItemWriter`
- T404-B: Configure `Partitioner` to split file records into chunks based on chunk size from `ConfigurationPort`
- T404-C: `ItemReader`: read CSV rows from `ObjectStoragePort` for the assigned partition range
- T404-D: `ItemProcessor`: call `ExternalApiPort.syncRecord()`, apply PII redaction before logging, return `RecordResult`
- T404-E: `ItemWriter`: persist batch of `RecordResult` entities in a single transaction
- T404-F: Configure `TaskExecutor` with thread pool size from `ConfigurationPort`

**Acceptance:** Batch job runs end-to-end for a 10-record test file; all `RecordResult` rows written to DB.

---

## T405 — Resilience4j Circuit Breaker and Retry

**Task:** Wrap `ExternalApiPort` calls with Resilience4j retry and circuit breaker.

**Subtasks:**
- T405-A: Add `resilience4j-spring-boot3` dependency to pom.xml
- T405-B: Configure `Retry` instance: max 3 attempts, exponential backoff (1s, 2s, 4s) from `ConfigurationPort`
- T405-C: Configure `CircuitBreaker` instance: 5 failures in 30s sliding window, 60s wait duration
- T405-D: Wrap `ExternalApiPort.syncRecord()` call in the `ItemProcessor` with `@CircuitBreaker` + `@Retry`
- T405-E: On circuit breaker OPEN: log with `ERROR` level, emit Prometheus metric, pause job (do not mark file FAILED)
- T405-F: Write unit test: mock API to always return 500 — verify retry count = 3, then `RecordStatus.FAILED`

**Acceptance:** Retry test passes; circuit breaker opens after 5 consecutive failures.

---

## T406 — Processing Worker (Event-Driven)

**Task:** Create the worker that consumes `ValidationCompletedEvent(PASS)` and launches the Batch job.

**Subtasks:**
- T406-A: Create `ProcessingWorker` subscribing to `cams.validation.completed` via `MessageConsumerPort`
- T406-B: Worker reads event, verifies `result == PASS`, transitions file to `PROCESSING` via state machine
- T406-C: Worker creates a `ProcessingJob` record and launches the Spring Batch job asynchronously
- T406-D: On job completion: update `ProcessingJob.status`, transition file to `COMPLETED` or `PARTIALLY_COMPLETED`, publish `ProcessingCompletedEvent`
- T406-E: On job failure (unhandled exception): transition file to `FAILED`, publish to DLQ

**Acceptance:** Happy path and failure path integration tests pass (Testcontainers + WireMock).

---

## T407 — Record Results API

**Task:** Expose record-level processing results via REST.

**Subtasks:**
- T407-A: Add `GET /api/v1/uploads/{fileId}/records` to `UploadController` with pagination and status filter
- T407-B: Response includes aggregate counts (synced, failed, skipped) and paginated record list
- T407-C: Failed records include redacted request payload, response code, error message
- T407-D: Add endpoint to `openapi.yaml`

**Acceptance:** Controller test passes; openapi.yaml validates.

---

## T408 — PII Redaction

**Task:** Implement field-level PII redaction before any logging or payload persistence.

**Subtasks:**
- T408-A: Create `PiiRedactionService` that accepts a record payload map and a list of PII field names (from template config)
- T408-B: Replace PII field values with `"[REDACTED]"` before storing `RecordResult.requestPayload`
- T408-C: Ensure MDC log statements never log raw record data — always use `PiiRedactionService` first
- T408-D: Write unit test: payload with PII fields → verify `[REDACTED]` appears in output

**Acceptance:** Unit test passes; no PII in any log line when test runs.

---

## T409 — Component Test (End-to-End Processing Pipeline)

**Task:** Write a Testcontainers component test for the full validation → processing pipeline.

**Subtasks:**
- T409-A: Create `ProcessingPipelineComponentTest` (`@Tag("component")`)
- T409-B: Use Testcontainers: PostgreSQL, RabbitMQ, MinIO, ClamAV, WireMock
- T409-C: Upload 10-record CSV, run through scan → validate → process
- T409-D: Assert `FileRecord.status = COMPLETED`, `ProcessingJob.syncedCount = 10`
- T409-E: Test partial failure: 2 records return `ERROR_TEST` → assert `PARTIALLY_COMPLETED`, `failedCount = 2`

**Acceptance:** Both scenarios pass in the component test suite.

---

## T410 — Update openapi.yaml for Epic 4 Endpoints

**Task:** Add record processing endpoints to `openapi.yaml` and bump to v1.3.0.

**Subtasks:**
- T410-A: Add `GET /uploads/{fileId}/records` with query params and response schema
- T410-B: Bump `info.version` to `1.3.0`

**Acceptance:** openapi.yaml validates with zero errors.
