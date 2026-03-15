# Epic 6: Cloud Portability via Interface + Adapter

## Overview

Every infrastructure concern in the CAMS platform — object storage, messaging, database, secrets, signed URLs, virus scanning — is accessed exclusively through a port interface. Cloud-specific implementations are activated by Spring `@Profile`. This epic completes the full adapter matrix: all remaining interfaces get a GCP adapter and a local (Docker) adapter, and a portability test proves the same business scenario produces identical outcomes in both profiles.

---

## User Stories

### US-601: ObjectStoragePort — GCS and MinIO Adapters

**As a** platform engineer,
**I want** all file read/write/move operations to go through `ObjectStoragePort`,
**So that** switching between GCS and MinIO requires only a profile change, not a code change.

**Acceptance Criteria:**

- AC-01: `ObjectStoragePort` interface defines: `store(objectName, inputStream)`, `retrieve(objectName) → InputStream`, `move(sourcePath, destPath)`, `delete(objectName)`, `exists(objectName) → boolean`.
- AC-02: `GcsObjectStorageAdapter` implements the interface using the GCS SDK, activated by `@Profile("gcp")`.
- AC-03: `LocalObjectStorageAdapter` implements the interface using the AWS SDK v2 S3 client pointed at MinIO, activated by `@Profile("local")`.
- AC-04: Both adapters pass all methods defined in `ObjectStorageContractTest` (abstract base class pattern).

### US-602: MessageConsumerPort — RabbitMQ and Pub/Sub Adapters

**As a** platform engineer,
**I want** all event consumption to go through `MessageConsumerPort`,
**So that** workers are decoupled from the underlying message broker.

**Acceptance Criteria:**

- AC-01: `MessageConsumerPort` provides a registration mechanism: `subscribe(topic, eventType, handler)`.
- AC-02: `RabbitMqMessageConsumer` implements the interface using Spring AMQP, activated by `@Profile("local")`.
- AC-03: `PubSubMessageConsumer` implements the interface using Spring Cloud GCP Pub/Sub, activated by `@Profile("gcp")`.
- AC-04: Both adapters handle message acknowledgement, dead-letter routing, and retry transparently.

### US-603: FileMetadataRepository as a Port

**As a** platform engineer,
**I want** the `FileRecord` persistence operations to go through a repository port interface,
**So that** the application layer is not coupled to Spanner or PostgreSQL.

**Acceptance Criteria:**

- AC-01: `FileMetadataRepository` interface defines: `save(FileRecord)`, `findById(fileId)`, `findByStatus(FileStatus)`, `updateStatus(fileId, newStatus, actor, reason)`.
- AC-02: `JpaFileMetadataRepository` implements using Spring Data JPA + PostgreSQL, activated by `@Profile("local")`.
- AC-03: `SpannerFileMetadataRepository` implements using Spring Data Spanner, activated by `@Profile("gcp")`.
- AC-04: `updateStatus` atomically updates `file_records.status` and inserts an audit row in `file_status_audit` in a single transaction.

### US-604: Portability Test — Same Scenario, Both Profiles

**As a** platform engineer,
**I want** a portability test that runs the full upload → scan → validate → process workflow against both the `local` and `gcp-emulator` profiles,
**So that** I have automated proof that no business logic has leaked into any adapter.

**Acceptance Criteria:**

- AC-01: `PortabilityTest` is an abstract base class with a full E2E scenario.
- AC-02: `LocalProfilePortabilityTest extends PortabilityTest` uses Testcontainers (PostgreSQL, MinIO, RabbitMQ, ClamAV).
- AC-03: `GcpEmulatorPortabilityTest extends PortabilityTest` uses Testcontainers (Spanner emulator, Pub/Sub emulator, Fake GCS server, ClamAV).
- AC-04: Both subclasses produce zero test failures.

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-601 | All port interfaces must reside in `com.cams.fileprocessing.interfaces` — no implementation class in that package. |
| FR-602 | All adapters must reside in `com.cams.fileprocessing.infrastructure.{gcp\|local\|aws}`. |
| FR-603 | ArchUnit rule must verify that no class outside `infrastructure.*` imports a GCP SDK, AWS SDK, or AMQP library class. |
| FR-604 | Every interface must have an abstract contract test base class in `src/test/.../contract/`. |
| FR-605 | Profile switching must be a single config change (`SPRING_PROFILES_ACTIVE=local` vs `gcp`) — no code changes required. |

---

## Non-Functional Requirements

| ID | Requirement | Target |
|---|---|---|
| NFR-601 | Profile switch time | Rebuild + restart only — no data migration |
| NFR-602 | Contract test coverage | 100% of interface methods tested in abstract base class |
| NFR-603 | ArchUnit violations | Zero on every build |
