# Implementation Plan: Epic 3 — Header Validation Pipeline

**Branch**: `003-header-validation` | **Date**: 2026-03-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-header-validation/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

This plan implements a dynamic, template-driven header validation pipeline. After a file is scanned clean, a `ValidationWorker` consumes `ScanCompletedEvent(CLEAN)`, reads only the first line of the file from object storage (streaming — no full load into memory), and validates the column structure against a `ValidationTemplate` retrieved from the database via `ValidationTemplatePort`. All column violations are collected in a single pass. Results are persisted to `validation_results` and `validation_errors`, and a `ValidationCompletedEvent` is then published. Templates are cached in memory (Caffeine, 60s TTL) and manageable via a runtime admin API without redeployment.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.2.3, Spring WebFlux, Spring AMQP/Pub/Sub, Caffeine Cache, Spring Data JPA (local), Spring Data Spanner (gcp), Testcontainers 1.19.8  
**Storage**: PostgreSQL (local) / Cloud Spanner (gcp) for templates and results; GCS / MinIO for file bytes (first-line streaming via `ObjectStoragePort`)  
**Testing**: JUnit 5, Testcontainers (PostgreSQL + RabbitMQ + MinIO + ClamAV), ArchUnit  
**Target Platform**: GKE (gcp profile); Docker Compose (local profile)  
**Project Type**: Event-driven worker + admin REST API  
**Performance Goals**: Validation throughput ≥ 200 files/min; header read latency < 2 seconds (p95); template cache hit latency < 5 ms  
**Constraints**: Only the first line of the file may be loaded into memory; all column errors collected in one pass (no fail-fast)  
**Scale/Scope**: Validates every ingested file; template changes take effect within 60 seconds (cache TTL)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **[PASS] Program to the Interface**: All template reads go through `ValidationTemplatePort`. `ValidationService` has zero direct DB imports.
- **[PASS] Local Adapter Mandatory**: `JpaValidationTemplateRepository` provides the local PostgreSQL implementation; `SpannerValidationTemplateRepository` provides the GCP implementation. Both activated by `@Profile`.
- **[PASS] No Mocks in Tests**: Validation pipeline component test uses real Testcontainers. `ValidationService` unit tests use a real in-memory template (no Mockito).
- **[PASS] Event-Driven First**: Worker is triggered by `ScanCompletedEvent` and produces `ValidationCompletedEvent`. No synchronous HTTP call triggers validation.
- **[PASS] Transactional Outbox**: `ValidationCompletedEvent` is published only after `validation_results` commit succeeds.
- **[PASS] ArchUnit Enforced**: Existing architecture rules plus a new rule verifying `business.validation` has no direct DB imports.

## Project Structure

### Documentation (this feature)

```text
specs/003-header-validation/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
src/
├── main/
│   └── java/com/cams/fileprocessing/
│       ├── interfaces/
│       │   └── ValidationTemplatePort.java
│       ├── business/validation/
│       │   ├── ValidationService.java
│       │   ├── ValidationTemplate.java
│       │   └── ColumnRule.java
│       ├── infrastructure/
│       │   ├── local/
│       │   │   └── JpaValidationTemplateRepository.java
│       │   └── gcp/
│       │       └── SpannerValidationTemplateRepository.java
│       └── features/validation/
│           ├── ValidationWorker.java
│           ├── ValidationTemplateController.java
│           └── models/
│               ├── ValidationResult.java
│               └── ValidationError.java
└── test/
    └── java/com/cams/fileprocessing/
        ├── business/validation/
        │   └── ValidationServiceTest.java          # unit — all 6 error codes
        ├── features/validation/
        │   └── ValidationControllerTest.java       # MockMvc
        ├── contract/
        │   ├── ValidationTemplateContractTest.java # abstract base
        │   └── JpaValidationTemplateContractTest.java
        └── component/
            └── ValidationPipelineComponentTest.java # @Tag("component")
```

**Structure Decision**: Single-project Spring Boot structure consistent with E1 and E2. Validation logic lives in `business/validation`, the event-driven worker in `features/validation`, and the adapters in `infrastructure/local` and `infrastructure/gcp`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *N/A*     | —          | —                                   |
