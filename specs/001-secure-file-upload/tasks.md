# Tasks: US-101: Secure File Upload Initiation

**Input**: Design documents from `/specs/001-secure-file-upload/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks. Tests are OPTIONAL - only include them if explicitly requested in the feature specification.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/`, `tests/` at repository root
- **Web app**: `backend/src/`, `frontend/src/`
- **Mobile**: `api/src/`, `ios/src/` or `android/src/`
- Paths shown below assume single project - adjust based on plan.md structure

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create the project structure in `src/main` and `src/test` as defined in `plan.md`
- [X] T002 Initialize a Spring Boot project with Java 21, Maven, and add primary dependencies: Spring WebFlux, Spring Cloud GCP (Storage), and Spring Data for Spanner in `pom.xml`
- [X] T003 [P] Configure basic logging in `src/main/resources/logback-spring.xml`
- [X] T004 [P] Configure base application properties for a `local` profile in `src/main/resources/application.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 Setup Spanner emulator and Pub/Sub emulator configuration for local testing, potentially using Testcontainers.
- [X] T006 [P] Implement a generic GCP configuration class in `src/main/java/com/cams/fileprocessing/config/GcpConfig.java` to manage GCP credentials and project ID.
- [X] T007 [P] Implement a base exception handler for REST APIs in `src/main/java/com/cams/fileprocessing/common/GlobalExceptionHandler.java` to standardize error responses.
- [X] T008 Create the main application class `src/main/java/com/cams/fileprocessing/CamsFileProcessingServiceApplication.java`.

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Securely Upload Large Financial Files (Priority: P1) 🎯 MVP

**Goal**: As a Portfolio Manager, I want to upload 1M+ record files via signed URLs so that files are securely received without size limits.

**Independent Test**: An API call generates a valid GCS pre-signed URL. A file can be uploaded to this URL, and a `FileRecord` is created in the database with status `AWAITING_UPLOAD`.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T009 [P] [US1] Write a contract test for the `requestUploadUrl` endpoint in `src/test/java/com/cams/fileprocessing/features/upload/UploadControllerTest.java` to verify the API against `contracts/openapi.yaml`.
- [X] T010 [P] [US1] Write an integration test in `src/test/java/com/cams/fileprocessing/features/upload/UploadServiceTest.java` to ensure the service correctly interacts with the GCS and Spanner emulators.

### Implementation for User Story 1

- [X] T011 [P] [US1] Create the `FileRecord` entity based on `data-model.md` in `src/main/java/com/cams/fileprocessing/features/upload/models/FileRecord.java`.
- [X] T012 [P] [US1] Create the Spring Data repository interface for the `FileRecord` entity in `src/main/java/com/cams/fileprocessing/features/upload/FileRecordRepository.java`.
- [X] T013 [US1] Implement the `GcsService` to generate pre-signed V4 URLs in `src/main/java/com/cams/fileprocessing/gcp/GcsService.java`.
- [X] T014 [US1] Implement the `UploadService` to orchestrate the creation of the `FileRecord` and generation of the pre-signed URL in `src/main/java/com/cams/fileprocessing/features/upload/UploadService.java`.
- [X] T015 [US1] Implement the `UploadController` with the `requestUploadUrl` endpoint defined in `contracts/openapi.yaml` in `src/main/java/com/cams/fileprocessing/features/upload/UploadController.java`.
- [X] T016 [US1] Add input validation for the `UploadRequest` object in the `UploadController` or `UploadService`.
- [X] T017 [US1] Add structured logging for all operations within the upload feature.

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T018 [P] Add comprehensive Javadoc to all new public classes and methods.
- [X] T019 [P] Enhance unit test coverage for services and utilities.
- [ ] T020 **[IAM Bucket Isolation Audit]** Verify and document that GCS/MinIO IAM rules enforce the quarantine-first security model:
  - Upload Service: WRITE-only to quarantine bucket (pre-signed URL scope restricts this automatically)
  - Scan Worker (Epic 2): READ-only on quarantine, WRITE-only on processing bucket
  - Validation/Processing Workers: NO access to quarantine bucket whatsoever
  - Document the verified rules in `specs/001-secure-file-upload/data-model.md` under "IAM Bucket Access Rules"
  - For GCP: verify via `gcloud storage buckets get-iam-policy`
  - For local profile: update MinIO bucket policies in `docker-compose.yml` or `minio-init` service
- [ ] T021 [P] Configure a lifecycle policy on the GCS "quarantine" bucket to automatically delete incomplete or old uploads (FR-007).
- [ ] T022 Validate the entire workflow described in `quickstart.md` to ensure it works end-to-end.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational phase completion.
- **Polish (Phase 4)**: Depends on User Story 1 completion.

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) is complete.

### Within Each User Story

- Tests (T009, T010) MUST be written and FAIL before implementation.
- Models (T011) and Repository (T012) before Services (T013, T014).
- Services (T013, T014) before Controller (T015).
- Core implementation before validation and logging (T016, T017).

### Parallel Opportunities

- **Setup**: T003 and T004 can run in parallel.
- **Foundational**: T006 and T007 can run in parallel.
- **User Story 1**:
  - T009 and T010 (Tests) can be developed in parallel.
  - T011 and T012 (Model/Repo) can be developed in parallel with T013 (GcsService).

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "T009 [P] [US1] Write a contract test for the requestUploadUrl endpoint..."
Task: "T010 [P] [US1] Write an integration test for the UploadService..."

# Launch parallel implementation tasks after tests are failing:
Task: "T011 [P] [US1] Create the FileRecord entity..."
Task: "T012 [P] [US1] Create the Spring Data repository interface..."
Task: "T013 [US1] Implement the GcsService..."
```

---

## Phase 5: US-102 — SFTP Ingress Channel

**Purpose**: Enable SFTP as a second ingress channel alongside REST.

- [ ] T023 [US-102] Add `sftp` service to `docker-compose.yml` using `atmoz/sftp:alpine` image; configure `inbound/` and `archive/` directories
- [ ] T024 [P] [US-102] Add `spring-integration-sftp` dependency to `pom.xml`
- [ ] T025 [US-102] Create `SftpIngressAdapter` that polls the SFTP `inbound/` directory on a configurable schedule (default 60s) and calls `UploadService.receiveFile()` for each new file
- [ ] T026 [P] [US-102] Add `SFTP` to the `IngressChannel` enum / constant set used by `FileRecord.ingressChannel`
- [ ] T027 [US-102] Implement SFTP file archiving: after successful processing, move the file from `inbound/` to `archive/{YYYY-MM-DD}/` — never delete
- [ ] T028 [US-102] Implement SFTP idempotency: track processed file names + sizes in `file_records` — skip files already in a non-PENDING_UPLOAD state
- [ ] T029 [US-102] Write `SftpIngressAdapterTest` using Testcontainers `atmoz/sftp` — verify file picked up, `FileRecord` created, file archived
- [ ] T030 [US-102] Update `openapi.yaml` comments/notes to reflect that the confirm endpoint is REST-only; SFTP files skip the signed-URL flow

---

## Phase 6: US-103 — GCS Bucket Trigger Ingress

**Purpose**: Enable GCS Pub/Sub notifications as a third ingress channel.

- [ ] T031 [US-103] Create `GcsBucketTriggerAdapter` (`@Profile("gcp")`) subscribing to `CAMS_INGEST_TOPIC` via `MessageConsumerPort`; extract `objectName` and `bucketName` from the Pub/Sub notification payload
- [ ] T032 [P] [US-103] Add `GCS_TRIGGER` to the `IngressChannel` constant set
- [ ] T033 [US-103] Map GCS object metadata labels (`flowType`, `priority`, `sourceName`) into `FileRecord` at creation time
- [ ] T034 [US-103] Implement deduplication: check `file_records` for existing entry with same `objectName + bucketName` before creating a new record
- [ ] T035 [P] [US-103] Create `LocalGcsTriggerSimulatorController` (`@Profile("local")`) at `POST /api/v1/dev/gcs-trigger` enabling manual trigger without a real GCS bucket
- [ ] T036 [US-103] Write `GcsTriggerAdapterTest` using Testcontainers Pub/Sub emulator — verify event received, `FileRecord` created with `ingressChannel=GCS_TRIGGER`
- [ ] T037 [US-103] Write `LocalGcsTriggerSimulatorTest` — verify the local endpoint creates a `FileRecord` and publishes `FileReceivedEvent`

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently using the steps in `quickstart.md`.
5. Deploy/demo if ready.

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready.
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!).
3. Each subsequent story adds value without breaking previous stories.
