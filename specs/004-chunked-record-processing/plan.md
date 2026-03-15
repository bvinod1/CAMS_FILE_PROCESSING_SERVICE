# Implementation Plan: Epic 4 — Chunked Record Processing

**Branch**: `004-chunked-record-processing` | **Date**: 2026-03-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-chunked-record-processing/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

This plan implements a partitioned Spring Batch job that processes validated file records in configurable chunks, calling an external synchronous API for each record. Chunk size and threading are configurable per flow type at runtime via `ConfigurationPort`. Resilience4j provides per-record retry (exponential backoff) and a circuit breaker for persistent downstream failures. Per-record outcomes are persisted to `record_results` (INSERT-only). On completion, the file transitions to `COMPLETED` or `PARTIALLY_COMPLETED` and a `ProcessingCompletedEvent` is published. PII fields are redacted from all logs and persisted payloads. The external API is abstracted behind `ExternalApiPort` — a WireMock adapter for local testing and an HTTP adapter for GCP.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.2.3, Spring Batch 5.x, Spring WebFlux, Resilience4j 2.x, WireMock 3.x (test), Testcontainers 1.19.8  
**Storage**: PostgreSQL (local) / Cloud Spanner (gcp) for job state and record results; GCS / MinIO for file bytes  
**Testing**: JUnit 5, WireMock (external API stub), Testcontainers (full stack), ArchUnit  
**Target Platform**: GKE (gcp profile); Docker Compose (local profile)  
**Project Type**: Event-driven batch worker + REST status API  
**Performance Goals**: 1M-record file processed in < 2 hours (p95); per-record API latency < 5 seconds; batch thread pool default 4 threads  
**Constraints**: ≤ 500 records in memory at any time (enforced by chunk size); Spring Batch state persisted to DB for crash recovery  
**Scale/Scope**: Processes every validated file; throughput controlled by chunk size and thread pool from runtime config

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **[PASS] Program to the Interface**: External API is accessed only via `ExternalApiPort`. `ItemProcessor` has no `HttpClient` import, no `WireMock` import.
- **[PASS] Local Adapter Mandatory**: `WireMockExternalApiAdapter` (`@Profile("local")`) provides a testable stub. `HttpExternalApiAdapter` (`@Profile("gcp")`) provides the real client.
- **[PASS] No Mocks in Tests**: Component test uses a real WireMock server via Testcontainers. No Mockito for infrastructure.
- **[PASS] Event-Driven First**: Processing is triggered by `ValidationCompletedEvent(PASS)`. No HTTP endpoint triggers a batch job.
- **[PASS] State Machine Gated**: `ProcessingWorker` calls `FileStateMachineService.transition(BEGIN_PROCESSING)` before launching the batch job — invalid transitions are caught.
- **[PASS] ArchUnit Enforced**: New rule verifying `business.processing` has no direct Spring Batch infrastructure imports.

## Project Structure

### Documentation (this feature)

```text
specs/004-chunked-record-processing/
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
│       │   └── ExternalApiPort.java
│       ├── business/processing/
│       │   └── PiiRedactionService.java
│       ├── infrastructure/
│       │   ├── local/
│       │   │   └── WireMockExternalApiAdapter.java
│       │   └── gcp/
│       │       └── HttpExternalApiAdapter.java
│       └── features/processing/
│           ├── ProcessingWorker.java
│           ├── ProcessingBatchConfig.java
│           ├── ProcessingItemProcessor.java
│           └── models/
│               ├── ProcessingJob.java
│               └── RecordResult.java
└── test/
    └── java/com/cams/fileprocessing/
        ├── business/processing/
        │   └── PiiRedactionServiceTest.java
        ├── contract/
        │   ├── ExternalApiContractTest.java        # abstract base
        │   └── LocalExternalApiContractTest.java
        └── component/
            └── ProcessingPipelineComponentTest.java # @Tag("component")
```

**Structure Decision**: Consistent single-project structure. Batch configuration lives in `features/processing` alongside the worker. `PiiRedactionService` lives in `business/processing` as it is pure domain logic with no infrastructure imports.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *N/A*     | —          | —                                   |
