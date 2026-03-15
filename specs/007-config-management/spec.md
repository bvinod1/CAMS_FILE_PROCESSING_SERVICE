# Epic 7: Configuration Management

## Overview

Business rules in the CAMS platform — validation templates, chunk sizes, retry policies, priority mappings, and feature flags — must be configurable at runtime without redeployment. This epic delivers a configuration management layer backed by the database in all environments, with an admin API for updates and an in-memory cache with a short TTL for performance.

---

## User Stories

### US-701: Validation Template CRUD via Admin API

**As a** data operations manager,
**I want** to create and update column validation rules for a given flow type via an API,
**So that** new file formats can be onboarded in minutes without a deployment.

**Acceptance Criteria:**

- AC-01: `POST /api/v1/admin/config/templates` creates a new validation template.
- AC-02: `PUT /api/v1/admin/config/templates/{flowType}` updates column rules and increments the template version.
- AC-03: `GET /api/v1/admin/config/templates/{flowType}` returns the current template with version history.
- AC-04: Template changes are effective immediately for subsequent validation jobs (cache TTL ≤ 60 seconds).
- AC-05: Attempting to delete a template referenced by an in-flight file returns `409 Conflict`.

### US-702: Feature Flags per Flow Type

**As a** platform engineer,
**I want** to enable or disable scanning, validation, and processing for a given flow type at runtime,
**So that** I can safely onboard a new flow type by testing individual pipeline stages before enabling them all.

**Acceptance Criteria:**

- AC-01: `PUT /api/v1/admin/config/flags/{flowType}` sets `scanning_enabled`, `validation_enabled`, `processing_enabled` flags.
- AC-02: When `scanning_enabled = false` for a flow type, the scan worker skips scanning and transitions directly to `SCANNED_CLEAN`.
- AC-03: When `validation_enabled = false`, the validation worker auto-passes validation.
- AC-04: Feature flag reads are cached in memory with a 30-second TTL.
- AC-05: All flag changes are audit-logged with timestamp and actor.

### US-703: Priority Rules Management

**As a** platform administrator,
**I want** to define which flow types are assigned to which priority queue,
**So that** NAV files and other time-sensitive flows always get P0 treatment.

**Acceptance Criteria:**

- AC-01: `PUT /api/v1/admin/config/priority` maps `flowType → priority level (0, 1, 2)`.
- AC-02: New `FileRecord` entries automatically inherit the priority from this mapping at creation time.
- AC-03: The priority mapping is cached in memory; cache refresh is triggered immediately on update.

### US-704: Processing Parameters

**As a** platform engineer,
**I want** chunk size, thread pool size, and retry configuration to be configurable per flow type,
**So that** bulk files and real-time NAV files can have different processing characteristics.

**Acceptance Criteria:**

- AC-01: `PUT /api/v1/admin/config/processing/{flowType}` sets `chunkSize`, `threadPoolSize`, `retryMaxAttempts`, `retryBackoffMs`.
- AC-02: The active Spring Batch job reads these values from the config store, not from `application.yml`.
- AC-03: Changes take effect on the next job invocation — in-flight jobs are not interrupted.

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-701 | All config reads must go through `ConfigurationPort` — no direct DB queries in business logic. |
| FR-702 | Config changes must be persisted atomically with a version number and audit entry. |
| FR-703 | The in-memory cache must use a `CaffeineCache` with a configurable TTL (default 60 seconds). |
| FR-704 | Template column rules are stored as a JSON column in the database — not a separate rows-per-column approach. |
| FR-705 | Local profile: all config is stored in PostgreSQL, accessible via Swagger UI at `http://localhost:8080/swagger-ui.html`. |

---

## Non-Functional Requirements

| ID | Requirement | Target |
|---|---|---|
| NFR-701 | Config read latency (cache hit, p99) | < 5 ms |
| NFR-702 | Config read latency (cache miss, p99) | < 50 ms |
| NFR-703 | API response time for config updates (p99) | < 200 ms |
