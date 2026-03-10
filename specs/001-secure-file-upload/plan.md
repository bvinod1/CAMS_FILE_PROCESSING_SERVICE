# Implementation Plan: US-101: Secure File Upload Initiation

**Branch**: `001-secure-file-upload` | **Date**: 2026-03-09 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-secure-file-upload/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

This plan outlines the work to implement `US-101`, a secure file upload initiation mechanism. The core of this feature is a Spring WebFlux API endpoint that generates a time-limited, pre-signed Google Cloud Storage (GCS) URL. This allows clients to upload large files (up to 5GB) directly and securely to a designated "quarantine" bucket. Upon URL generation, a preliminary record is created in a Cloud Spanner database to track the file's lifecycle, starting with an `AWAITING_UPLOAD` status.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.x, Spring WebFlux, Spring Cloud GCP (Storage), OpenAPI 3.0, JUnit 5
**Storage**: Google Cloud Storage (GCS) for files, Cloud Spanner for metadata
**Testing**: JUnit 5
**Target Platform**: Google Kubernetes Engine (GKE)
**Project Type**: Web-service
**Performance Goals**: < 200ms (p99) latency for the pre-signed URL generation API under 1000 concurrent requests.
**Constraints**: Support for files up to 5GB, pre-signed URLs must expire (default 24 hours).
**Scale/Scope**: Initial ingress point for all file processing, must handle large financial files (1M+ records).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **[PASS] Event-Driven First**: The feature correctly implements the initial step of an event-driven workflow. It avoids handling file streams directly in the API and prepares for a subsequent event-based trigger upon file arrival in GCS.
- **[PASS] Asynchronous Processing is Mandatory**: The use of pre-signed URLs for direct-to-GCS uploads is a fully asynchronous pattern, aligning perfectly with the constitution. The API's responsibility ends after providing the URL.
- **[PASS] Horizontal Scalability by Design**: The API endpoint is stateless, making it suitable for horizontal scaling on GKE as mandated.
- **[PASS] Reliability Through Idempotency and State**: The plan includes creating a file record in Spanner at the start, which is the first step in tracking state through the finite state machine (FSM).
- **[PASS] Spec-Driven Development**: The feature will be driven by an OpenAPI specification, which will be created in Phase 1.

## Project Structure

### Documentation (this feature)

```text
specs/001-secure-file-upload/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
```text
# Option 1: Single project (DEFAULT)
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── cams/
│   │           └── fileprocessing/
│   │               ├── CamsFileProcessingServiceApplication.java
│   │               ├── common/
│   │               ├── config/
│   │               ├── features/
│   │               │   └── upload/
│   │               │       ├── UploadController.java
│   │               │       ├── UploadService.java
│   │               │       ├── FileRecordRepository.java
│   │               │       └── models/
│   │               │           └── FileRecord.java
│   │               └── gcp/
│   │                   └── GcsService.java
│   └── resources/
│       ├── application.yml
│       └── logback-spring.xml
└── test/
    └── java/
        └── com/
            └── cams/
                └── fileprocessing/
                    └── features/
                        └── upload/
                            ├── UploadControllerTest.java
                            └── UploadServiceTest.java
```

**Structure Decision**: A standard single-project Spring Boot structure is chosen. The core logic for the secure upload feature will be encapsulated within `src/main/java/com/cams/fileprocessing/features/upload`. This provides clear separation of concerns while remaining simple for the initial service.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *N/A*     | -          | -                                   |
