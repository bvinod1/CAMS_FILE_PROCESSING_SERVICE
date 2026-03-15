# Implementation Plan: Epic 5 — File Lifecycle State Machine

**Branch**: `005-state-machine` | **Date**: 2026-03-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/005-state-machine/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

This plan implements Spring State Machine as the single authoritative gatekeeper for all `FileRecord` status transitions. `FileStateMachineService` reconstructs the machine from the persisted state on every operation (stateless between calls), sends the appropriate event, and atomically commits both the `file_records.status` UPDATE and the `file_status_audit` INSERT in one transaction. Invalid transitions throw `InvalidStateTransitionException` which routes the file to `FAILED` via an emergency fallback path. Files entering terminal failure states automatically trigger DLQ publication via `MessagePublisherPort`. Priority queues (P0/P1/P2) are provisioned in RabbitMQ (local) and documented for Pub/Sub (gcp).

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.2.3, Spring State Machine 3.x, Spring AMQP (local), Spring Cloud GCP Pub/Sub (gcp), Spring Data JPA (local), Spring Data Spanner (gcp)  
**Storage**: PostgreSQL (local) / Cloud Spanner (gcp) — `file_records`, `file_status_audit`  
**Testing**: JUnit 5, Testcontainers (PostgreSQL + RabbitMQ), ArchUnit  
**Target Platform**: GKE (gcp profile); Docker Compose (local profile)  
**Project Type**: Domain service (state machine) + admin REST API  
**Performance Goals**: State transition latency < 100 ms (p99, including DB write + event publish); audit trail query < 200 ms for 50 transitions  
**Constraints**: State machine is stateless per call — no machine held in memory between invocations; every transition DB write must be atomic with audit INSERT  
**Scale/Scope**: All 14 FileStatus states and 14 valid transitions; handles all pipeline stages concurrently

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **[PASS] Program to the Interface**: `FileStateMachineService` depends only on `FileMetadataRepository` (port interface) and `MessagePublisherPort` (port interface). No direct DB or broker imports in business layer.
- **[PASS] Transactional Integrity**: `FileMetadataRepository.updateStatus()` wraps the status UPDATE and audit INSERT in a single `@Transactional` boundary. The machine cannot succeed without the audit row.
- **[PASS] No Mocks in Tests**: State machine integration test uses Testcontainers PostgreSQL. No Mockito for persistence or messaging.
- **[PASS] Idempotency**: Sending the same event twice when already in the target state is a safe no-op — enforced by state machine accept rules.
- **[PASS] ArchUnit Enforced**: New rule confirming only `FileStateMachineService` is permitted to call `FileMetadataRepository.updateStatus()`.
- **[PASS] DLQ Publishing**: Terminal failure transitions always publish `FileFailedEvent` to `MessagePublisherPort` — never lost because it is published after the DB commit.

## Project Structure

### Documentation (this feature)

```text
specs/005-state-machine/
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
│       ├── features/statemachine/
│       │   ├── FileStateMachineConfig.java    # StateMachineConfigurerAdapter
│       │   ├── FileStateMachineService.java   # Business facade
│       │   ├── FileState.java                 # enum (14 states)
│       │   ├── FileEvent.java                 # enum (14 events)
│       │   ├── FileStateMapper.java           # FileState ↔ FileStatus
│       │   └── FileFailedEvent.java           # DLQ event record
│       └── features/admin/
│           └── AdminController.java           # DLQ list + retry endpoints
└── test/
    └── java/com/cams/fileprocessing/
        ├── features/statemachine/
        │   ├── FileStateMachineServiceTest.java   # unit — transitions
        │   └── StateMachineIntegrationTest.java   # Testcontainers PostgreSQL
        ├── features/admin/
        │   └── AdminControllerTest.java
        └── arch/
            └── PortAdapterArchitectureTest.java   # expanded rules
```

**Structure Decision**: State machine configuration and service live in `features/statemachine` — it is a domain feature, not infrastructure. The admin controller for DLQ management lives in `features/admin`. ArchUnit tests already exist in `arch/` and are extended here.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *N/A*     | —          | —                                   |
