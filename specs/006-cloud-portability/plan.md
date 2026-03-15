# Implementation Plan: Epic 6 — Cloud Portability via Interface + Adapter

**Branch**: `006-cloud-portability` | **Date**: 2026-03-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-cloud-portability/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

This plan completes the full interface/adapter matrix defined in the constitution. Three new port interfaces are introduced — `ObjectStoragePort`, `MessageConsumerPort`, and `FileMetadataRepository` (formalised as a port) — each with a local Docker adapter and a GCP adapter. Abstract contract test base classes ensure all adapter implementations are functionally equivalent. An ArchUnit rule verifies no cloud SDK ever leaks into the business layer. A portability integration test drives the full upload → scan → validate → process pipeline against both the `local` profile (Testcontainers: PostgreSQL, MinIO, RabbitMQ, ClamAV) and the `gcp-emulator` profile (Testcontainers: Spanner emulator, Pub/Sub emulator, fake-gcs-server, ClamAV).

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.2.3, AWS SDK v2 S3 (MinIO adapter), Spring Cloud GCP Storage + Pub/Sub + Spanner, Spring AMQP, Testcontainers (MinIO module, Spanner emulator, Pub/Sub emulator, fake-gcs-server), ArchUnit 1.3.0  
**Storage**: PostgreSQL (local) / Cloud Spanner (gcp); MinIO (local) / GCS (gcp)  
**Testing**: JUnit 5, abstract contract test bases, full portability integration test (`@Tag("portability")`)  
**Target Platform**: GKE (gcp profile); Docker Compose (local profile)  
**Project Type**: Infrastructure completeness + cross-cutting architecture validation  
**Performance Goals**: Profile switch requires only config change — no code change, no data migration  
**Constraints**: Zero ArchUnit violations on every build; 100% of interface methods covered by abstract contract test base class  
**Scale/Scope**: Covers all 7 port interfaces in the constitution interface table; completes the portability matrix

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **[PASS] Program to the Interface — completing the matrix**: After this epic, every infrastructure concern in the constitution interface table has both a local and a GCP adapter with a contract test.
- **[PASS] Local Adapter Mandatory**: Every new port gets a local Docker adapter. No new port is GCP-only.
- **[PASS] No Mocks in Tests**: Contract tests use real containers (MinIO, Spanner emulator, Pub/Sub emulator). No Mockito for infrastructure in contract or portability tests.
- **[PASS] ArchUnit Enforced**: Four new ArchUnit rules added (cloud SDK leakage, interface purity, GCP profile enforcement, local profile enforcement).
- **[PASS] Portability Proof**: The abstract `PortabilityTest` + two subclasses proves the same business scenario works across both profiles — automated, not manual.

## Project Structure

### Documentation (this feature)

```text
specs/006-cloud-portability/
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
│       │   ├── ObjectStoragePort.java         # NEW
│       │   ├── MessageConsumerPort.java       # NEW
│       │   └── FileMetadataRepository.java    # FORMALISED as port
│       └── infrastructure/
│           ├── local/
│           │   ├── LocalObjectStorageAdapter.java    # MinIO / AWS SDK v2
│           │   ├── RabbitMqMessageConsumer.java      # Spring AMQP
│           │   └── JpaFileMetadataRepository.java    # Spring Data JPA
│           └── gcp/
│               ├── GcsObjectStorageAdapter.java      # GCS SDK
│               ├── PubSubMessageConsumer.java        # Spring Cloud GCP
│               └── SpannerFileMetadataRepository.java
└── test/
    └── java/com/cams/fileprocessing/
        ├── contract/
        │   ├── ObjectStorageContractTest.java       # abstract
        │   ├── LocalObjectStorageContractTest.java  # Testcontainers MinIO
        │   ├── GcsObjectStorageContractTest.java    # Testcontainers fake-gcs-server
        │   ├── MessageConsumerContractTest.java     # abstract
        │   ├── RabbitMqConsumerContractTest.java    # Testcontainers RabbitMQ
        │   ├── PubSubConsumerContractTest.java      # Testcontainers Pub/Sub emulator
        │   ├── FileMetadataRepositoryContractTest.java # abstract
        │   ├── JpaRepositoryContractTest.java       # Testcontainers PostgreSQL
        │   └── SpannerRepositoryContractTest.java   # Testcontainers Spanner
        └── portability/
            ├── PortabilityTest.java                 # abstract E2E scenario
            ├── LocalProfilePortabilityTest.java     # @Tag("portability")
            └── GcpEmulatorPortabilityTest.java      # @Tag("portability")
```

**Structure Decision**: Consistent single-project structure. New interfaces in `interfaces/`, new adapters in `infrastructure/local` and `infrastructure/gcp`. Contract tests in `contract/`, portability tests in `portability/` — separate from component tests to allow independent CI tagging.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *N/A*     | —          | —                                   |
