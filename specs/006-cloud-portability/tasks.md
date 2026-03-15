# Tasks — Epic 6: Cloud Portability via Interface + Adapter

## T601 — ObjectStoragePort Interface and Contract Test

**Task:** Define the `ObjectStoragePort` interface and write the abstract contract test.

**Subtasks:**
- T601-A: Create `ObjectStoragePort` interface in `com.cams.fileprocessing.interfaces` with 5 methods: `store`, `retrieve`, `move`, `delete`, `exists`
- T601-B: Create abstract `ObjectStorageContractTest` in `src/test/.../contract/` with test methods for all 5 operations
- T601-C: Verify the abstract test compiles; it will fail until concrete subclasses are created (T602, T603)

**Acceptance:** Interface and abstract test compile with zero errors.

---

## T602 — LocalObjectStorageAdapter (MinIO)

**Task:** Implement `ObjectStoragePort` using the AWS S3 SDK v2 pointed at the MinIO Docker container.

**Subtasks:**
- T602-A: Add `software.amazon.awssdk:s3` dependency to pom.xml scoped appropriately
- T602-B: Add MinIO service to `docker-compose.yml` (image `minio/minio:RELEASE.2024-01-16T16-07-38Z`, port 9000)
- T602-C: Create `LocalObjectStorageAdapter` (`@Profile("local")`) in `com.cams.fileprocessing.infrastructure.local`
- T602-D: Configure `S3Client` bean via `application-local.yml` pointing to `http://localhost:9000`
- T602-E: Create `LocalObjectStorageContractTest extends ObjectStorageContractTest` using Testcontainers MinIO module

**Acceptance:** `LocalObjectStorageContractTest` passes all 5 inherited test methods.

---

## T603 — GcsObjectStorageAdapter

**Task:** Implement `ObjectStoragePort` using the GCS SDK.

**Subtasks:**
- T603-A: Create `GcsObjectStorageAdapter` (`@Profile("gcp")`) in `com.cams.fileprocessing.infrastructure.gcp`
- T603-B: Implement all 5 port methods using `com.google.cloud.storage.Storage`
- T603-C: Create `GcsObjectStorageContractTest extends ObjectStorageContractTest` using Testcontainers `fake-gcs-server` image
- T603-D: Configure GCS client to point to the fake server via `StorageOptions.newBuilder().setHost(...)`

**Acceptance:** `GcsObjectStorageContractTest` passes all 5 inherited test methods.

---

## T604 — MessageConsumerPort Interface and Contract Test

**Task:** Define `MessageConsumerPort` and write the abstract contract test.

**Subtasks:**
- T604-A: Create `MessageConsumerPort` interface: `subscribe(topic, eventType, handler)`, `acknowledge(messageId)`, `nack(messageId, reason)`
- T604-B: Create abstract `MessageConsumerContractTest` testing subscribe → receive → ack and subscribe → receive → nack flows
- T604-C: Review `MessagePublisherPort` (already exists) — ensure it is symmetrical with the new consumer port

**Acceptance:** Interface and abstract test compile.

---

## T605 — RabbitMqMessageConsumer

**Task:** Implement `MessageConsumerPort` using Spring AMQP for the local profile.

**Subtasks:**
- T605-A: Create `RabbitMqMessageConsumer` (`@Profile("local")`) in `com.cams.fileprocessing.infrastructure.local`
- T605-B: Implement `subscribe()` by dynamically registering a `@RabbitListener` on the given queue name
- T605-C: Wire DLX: nack'd messages route to `cams.dlq` queue
- T605-D: Create `RabbitMqConsumerContractTest extends MessageConsumerContractTest` using Testcontainers RabbitMQ

**Acceptance:** Contract test passes; DLX routing verified.

---

## T606 — PubSubMessageConsumer

**Task:** Implement `MessageConsumerPort` using Spring Cloud GCP Pub/Sub for the GCP profile.

**Subtasks:**
- T606-A: Create `PubSubMessageConsumer` (`@Profile("gcp")`) in `com.cams.fileprocessing.infrastructure.gcp`
- T606-B: Implement subscription via `PubSubTemplate.subscribeAndConvert()`
- T606-C: Propagate `traceparent` header from Pub/Sub message attributes
- T606-D: Create `PubSubConsumerContractTest extends MessageConsumerContractTest` using Testcontainers Pub/Sub emulator

**Acceptance:** Contract test passes; trace propagation verified.

---

## T607 — FileMetadataRepository Port and JPA Implementation

**Task:** Refactor `FileRecordRepository` to implement `FileMetadataRepository` port interface.

**Subtasks:**
- T607-A: Create `FileMetadataRepository` interface in `com.cams.fileprocessing.interfaces`
- T607-B: Rename/refactor existing `FileRecordRepository` to `JpaFileMetadataRepository` (`@Profile("local")`) implementing the port
- T607-C: Implement `updateStatus()` atomically: `file_records` UPDATE + `file_status_audit` INSERT in one `@Transactional` method
- T607-D: Update all injection sites: change type from `FileRecordRepository` to `FileMetadataRepository`
- T607-E: Create abstract `FileMetadataRepositoryContractTest` and `JpaRepositoryContractTest` subclass

**Acceptance:** All 9+ existing tests still pass; `mvn test` green.

---

## T608 — SpannerFileMetadataRepository

**Task:** Implement `FileMetadataRepository` using Spring Data Spanner.

**Subtasks:**
- T608-A: Create `SpannerFileMetadataRepository` (`@Profile("gcp")`) in `com.cams.fileprocessing.infrastructure.gcp`
- T608-B: Map `FileRecord` entity to Spanner using `@Table`, `@Column` Spring Data Spanner annotations
- T608-C: Implement `updateStatus()` using Spanner read-write transaction
- T608-D: Create `SpannerRepositoryContractTest extends FileMetadataRepositoryContractTest` using Testcontainers Spanner emulator

**Acceptance:** Spanner contract test passes.

---

## T609 — Portability ArchUnit Rules

**Task:** Add ArchUnit rules enforcing strict port/adapter separation.

**Subtasks:**
- T609-A: Rule: no class in `business.*` or `features.*` imports any class from `com.google.cloud.*`, `software.amazon.awssdk.*`, or `com.rabbitmq.*`
- T609-B: Rule: no class in `interfaces.*` has a simple name ending in `Adapter` or `Repository`
- T609-C: Rule: all classes in `infrastructure.gcp.*` are annotated with `@Profile("gcp")`
- T609-D: Rule: all classes in `infrastructure.local.*` are annotated with `@Profile("local")` or `@Profile("local", "default")`

**Acceptance:** All 4 new ArchUnit rules pass; zero violations on `mvn test`.

---

## T610 — Portability Integration Test

**Task:** Create the abstract portability test and both profile-specific subclasses.

**Subtasks:**
- T610-A: Create abstract `PortabilityTest` with an E2E scenario: upload → scan (clean) → validate → process (10 records)
- T610-B: Create `LocalProfilePortabilityTest extends PortabilityTest` using Testcontainers (PostgreSQL, MinIO, RabbitMQ, ClamAV)
- T610-C: Create `GcpEmulatorPortabilityTest extends PortabilityTest` using Testcontainers (Spanner emulator, Pub/Sub emulator, fake-gcs-server, ClamAV)
- T610-D: Both subclasses tagged `@Tag("portability")` for selective CI execution
- T610-E: Add Makefile target `make test-portability` that runs `mvn test -Dgroups=portability`

**Acceptance:** Both portability tests produce zero failures.
