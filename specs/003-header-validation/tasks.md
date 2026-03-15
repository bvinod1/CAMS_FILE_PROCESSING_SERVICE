# Tasks — Epic 3: Header Validation Pipeline

## T301 — Validation Template Domain Model

**Task:** Create `ValidationTemplate`, `ColumnRule`, `ValidationResult`, and `ValidationError` domain objects.

**Subtasks:**
- T301-A: Create `com.cams.fileprocessing.features.validation.models.ValidationTemplate` record (flowType, version, columnRules list, active flag)
- T301-B: Create `com.cams.fileprocessing.features.validation.models.ColumnRule` record (name, position, dataType, required, allowNull, maxLength, pattern)
- T301-C: Create `com.cams.fileprocessing.features.validation.models.ValidationResult` record (fileId, flowType, templateVersion, result enum, errorCount, durationMs)
- T301-D: Create `ValidationResultStatus` enum: `PASS`, `FAIL`, `UNPARSEABLE_HEADER`

**Acceptance:** Domain objects compile; no infrastructure imports in model layer.

---

## T302 — ValidationTemplatePort and Database Schema

**Task:** Create the port interface and database tables for validation templates.

**Subtasks:**
- T302-A: Create `com.cams.fileprocessing.interfaces.ValidationTemplatePort` interface (getActiveTemplate, saveTemplate, listTemplates)
- T302-B: Create `db/init/02-validation-templates.sql` with tables `validation_templates`, `validation_results`, `validation_errors` plus INSERT-only grants on `validation_errors`
- T302-C: Seed template data for `NAV` and `TRANSACTION` flow types with their column schemas
- T302-D: Create `JpaValidationTemplateRepository` (`@Profile("local")`) and `SpannerValidationTemplateRepository` (`@Profile("gcp")`) implementing the port

**Acceptance:** `mvn test` — no migration errors; queries return seeded data.

---

## T303 — In-Memory Template Cache

**Task:** Wrap `ValidationTemplatePort` with a Caffeine cache (60s TTL).

**Subtasks:**
- T303-A: Add `spring-boot-starter-cache` + `caffeine` to pom.xml if not present
- T303-B: Create `CachingValidationTemplateService` that delegates to the port and applies `@Cacheable("validationTemplateCache")`
- T303-C: Add `@CacheEvict` on the update method
- T303-D: Write unit test: verify DB is called only once for two reads within TTL window (Mockito verify)

**Acceptance:** Cache hit test passes; DB call count correct.

---

## T304 — Header Validation Service

**Task:** Implement the core validation logic that reads the first line of a file and compares it against the template.

**Subtasks:**
- T304-A: Create `ValidationService` in `com.cams.fileprocessing.business.validation`
- T304-B: Implement `validate(fileId, flowType) → ValidationResult` — reads first line via `ObjectStoragePort`, resolves template, runs column checks
- T304-C: Implement all error code checks: `MISSING_COLUMN`, `WRONG_POSITION`, `EXTRA_COLUMN`, `UNPARSEABLE_HEADER`, `NULL_NOT_ALLOWED`
- T304-D: Ensure all column errors are collected in a single pass (no fail-fast)
- T304-E: Return `PASS` immediately if all column rules satisfied

**Acceptance:** Unit tests cover all 6 error codes + happy path.

---

## T305 — Validation Worker (Event-Driven)

**Task:** Create the worker that consumes `ScanCompletedEvent(CLEAN)` and invokes the validation service.

**Subtasks:**
- T305-A: Create `ValidationWorker` subscribing to `cams.scan.completed` via `MessageConsumerPort`
- T305-B: Worker reads `ScanCompletedEvent`, checks `result == CLEAN`, then calls `FileStateMachine.transition(VALIDATING)`
- T305-C: Worker calls `ValidationService.validate()`, persists `ValidationResult` and `ValidationError` rows in transaction
- T305-D: After DB commit, call `FileStateMachine.transition(VALIDATED or VALIDATION_FAILED)` then publish `ValidationCompletedEvent`
- T305-E: Set MDC fields (`fileId`, `flowType`, `status`) at entry; clear at exit

**Acceptance:** Integration test (Testcontainers PostgreSQL + RabbitMQ) — event in → correct DB state + event out.

---

## T306 — Validation Template Admin API

**Task:** Implement REST endpoints for template CRUD.

**Subtasks:**
- T306-A: Create `ValidationTemplateController` with `POST /api/v1/validation-templates` and `PUT /api/v1/validation-templates/{flowType}`
- T306-B: Implement `GET /api/v1/validation-templates/{flowType}` returning current template + version history
- T306-C: Add `409 Conflict` response when attempting to delete a template with in-flight files
- T306-D: All mutations audit-logged in `config_audit` table

**Acceptance:** ControllerTest passes for all 4 endpoints with MockMvc.

---

## T307 — Validation Error API

**Task:** Expose validation errors for a file via REST.

**Subtasks:**
- T307-A: Add `GET /api/v1/uploads/{fileId}/validation-errors` to `UploadController`
- T307-B: Return `404` if no validation result exists for the file, `200` if result exists (even for PASS — empty errors list)
- T307-C: Add endpoint to `openapi.yaml`

**Acceptance:** Controller test with mock data; openapi.yaml updated and schema validates.

---

## T308 — ValidationTemplatePort ArchUnit Rule

**Task:** Add ArchUnit rule ensuring `ValidationService` never imports a repository class directly.

**Subtasks:**
- T308-A: Add rule to `PortAdapterArchitectureTest`: no class in `business.validation` depends on `infrastructure.*` or `JpaRepository`
- T308-B: Run ArchUnit test to confirm no violations

**Acceptance:** All 5 ArchUnit rules pass.

---

## T309 — Component Test (End-to-End Validation Pipeline)

**Task:** Write a Testcontainers component test that runs the full scan → validate pipeline.

**Subtasks:**
- T309-A: Create `ValidationPipelineComponentTest` (`@Tag("component")`)
- T309-B: Use Testcontainers: PostgreSQL, RabbitMQ, MinIO, ClamAV
- T309-C: Upload a dummy CSV, trigger scan, assert `ValidationCompletedEvent(PASS)` is received and `file_records.status = VALIDATED`
- T309-D: Upload a CSV with wrong header order, assert `ValidationCompletedEvent(FAIL)` and `validation_errors` table has correct rows

**Acceptance:** Component test passes locally via `mvn test -Dgroups=component`.

---

## T310 — Update openapi.yaml for Epic 3 Endpoints

**Task:** Add all new Epic 3 endpoints to `specs/001-secure-file-upload/contracts/openapi.yaml` and bump to v1.2.0.

**Subtasks:**
- T310-A: Add `GET /uploads/{fileId}/validation-errors` with full response schema
- T310-B: Add `POST /validation-templates` and `PUT /validation-templates/{flowType}` with request/response schemas
- T310-C: Bump `info.version` to `1.2.0`
- T310-D: Validate openapi.yaml with `swagger-parser` (or online validator)

**Acceptance:** openapi.yaml validates with zero errors.
