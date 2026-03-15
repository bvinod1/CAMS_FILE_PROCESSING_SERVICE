# Tasks — Epic 7: Configuration Management

## T701 — ConfigurationPort Interface

**Task:** Define the `ConfigurationPort` interface for all runtime config reads and writes.

**Subtasks:**
- T701-A: Create `ConfigurationPort` interface in `com.cams.fileprocessing.interfaces` with methods: `getProcessingConfig`, `getFeatureFlags`, `getPriorityLevel`, `updateFeatureFlags`, `updateProcessingConfig`, `updatePriorityRule`
- T701-B: Create `ProcessingConfig` record (flowType, chunkSize, threadPoolSize, retryMaxAttempts, retryBackoffMs, externalApiUrl, externalApiTimeoutMs)
- T701-C: Create `FeatureFlags` record (flowType, scanningEnabled, validationEnabled, processingEnabled, strictHeaderMode, piiRedactionEnabled)

**Acceptance:** Interface and records compile; no infrastructure imports.

---

## T702 — Database Schema for Config Tables

**Task:** Add config tables to the database schema.

**Subtasks:**
- T702-A: Add `config_templates`, `feature_flags`, `priority_rules`, `config_audit` DDL to `db/init/01-schema.sql`
- T702-B: Add INSERT-only grant on `config_audit`
- T702-C: Create `db/init/03-config-seed.sql` with NAV and TRANSACTION seed data (processing config, feature flags, priority rules)
- T702-D: Verify Flyway or init script ordering: `01-schema.sql → 02-validation-templates.sql → 03-config-seed.sql`

**Acceptance:** `docker-compose up` — all 3 init scripts run cleanly; seed data present.

---

## T703 — PostgresConfigurationRepository (Local Profile)

**Task:** Implement `ConfigurationPort` using Spring Data JPA for the local profile.

**Subtasks:**
- T703-A: Create `PostgresConfigurationRepository` (`@Profile("local")`) implementing `ConfigurationPort`
- T703-B: Inject `JpaRepository` beans for `ConfigTemplate`, `FeatureFlag`, `PriorityRule` entities
- T703-C: Implement `update*` methods with `@Transactional` and write to `config_audit` table
- T703-D: Write integration test using Testcontainers PostgreSQL — test read after update returns updated value

**Acceptance:** Integration test passes; audit table populated on all updates.

---

## T704 — Caffeine Cache Layer

**Task:** Wrap `ConfigurationPort` with a Caffeine in-memory cache.

**Subtasks:**
- T704-A: Add Caffeine dependency to pom.xml if not present
- T704-B: Create `CachingConfigurationService` that delegates all reads to the underlying port with `@Cacheable`
- T704-C: Apply `@CacheEvict` on all `update*` methods for immediate cache invalidation
- T704-D: Configure cache TTLs: `processingConfigCache=60s`, `featureFlagsCache=30s`, `priorityRulesCache=30s`
- T704-E: Write unit test: read → update → read again — verify cache is invalidated and fresh value returned

**Acceptance:** Cache unit test passes; DB call count verified with Mockito.

---

## T705 — Config Admin REST API

**Task:** Expose configuration CRUD via REST endpoints in an admin controller.

**Subtasks:**
- T705-A: Create `ConfigAdminController` with:
  - `PUT /api/v1/admin/config/templates/{flowType}` (processing parameters)
  - `GET /api/v1/admin/config/templates/{flowType}`
  - `PUT /api/v1/admin/config/flags/{flowType}` (feature flags)
  - `GET /api/v1/admin/config/flags/{flowType}`
  - `PUT /api/v1/admin/config/priority` (bulk priority mapping)
  - `GET /api/v1/admin/config/priority`
- T705-B: Inject `CachingConfigurationService` (not the raw port)
- T705-C: All mutations require `ROLE_ADMIN` — document in openapi.yaml security schemes
- T705-D: Write controller tests for all endpoints (5 GET + 3 PUT)

**Acceptance:** All 8 controller tests pass; 401 returned without admin token.

---

## T706 — Feature Flag Runtime Enforcement

**Task:** Enforce feature flags at runtime in each pipeline worker.

**Subtasks:**
- T706-A: In `ScanService`: check `ConfigurationPort.getFeatureFlags(flowType).scanningEnabled()` — if false, skip ClamAV and transition directly to `SCANNED_CLEAN`
- T706-B: In `ValidationService`: check `validationEnabled` — if false, auto-pass and publish `ValidationCompletedEvent(PASS)`
- T706-C: In `ProcessingWorker`: check `processingEnabled` — if false, skip batch job and transition directly to `COMPLETED`
- T706-D: Write unit tests for each bypassed worker: flag=false → correct transition, no external call made

**Acceptance:** 3 feature-flag bypass tests pass.

---

## T707 — Priority Injection at Upload Time

**Task:** Inject the priority level into new file records from the priority rules config.

**Subtasks:**
- T707-A: In `UploadService.createUploadUrl()`: after determining `flowType`, call `ConfigurationPort.getPriorityLevel(flowType)` and set `FileRecord.priority`
- T707-B: Verify the priority field is mapped to the correct queue route in `MessagePublisherPort`
- T707-C: Write unit test: `flowType="NAV"` → priority = P0 injected; `flowType="TRANSACTION"` → P1

**Acceptance:** Priority injection unit tests pass; test verifies queue routing.

---

## T708 — Config Audit Trail

**Task:** Verify all config changes are audit-logged.

**Subtasks:**
- T708-A: Review all `update*` methods in `PostgresConfigurationRepository` — ensure every one writes to `config_audit`
- T708-B: Verify `config_audit` is INSERT-only at the DB level (`REVOKE UPDATE, DELETE`)
- T708-C: Add `GET /api/v1/admin/config/audit?flowType={flowType}` endpoint returning paginated audit history

**Acceptance:** Audit endpoint returns expected history after 3 config updates.

---

## T709 — SpannerConfigurationRepository (GCP Profile)

**Task:** Implement `ConfigurationPort` for GCP using Spring Data Spanner.

**Subtasks:**
- T709-A: Create `SpannerConfigurationRepository` (`@Profile("gcp")`) in `com.cams.fileprocessing.infrastructure.gcp`
- T709-B: Map all three config entities to Spanner tables
- T709-C: Create Spanner DDL equivalents of the 4 config tables in `db/spanner/` folder

**Acceptance:** Spanner implementation compiles; entity annotations validate against schema.

---

## T710 — Add Config Endpoints to openapi.yaml

**Task:** Document all new admin config endpoints in the OpenAPI contract.

**Subtasks:**
- T710-A: Add all 8 `/api/v1/admin/config/*` endpoints with request/response schemas
- T710-B: Add `bearerAuth` security scheme and mark config endpoints as requiring `ROLE_ADMIN`
- T710-C: Bump `info.version` to `1.4.0`

**Acceptance:** openapi.yaml validates with zero errors.
