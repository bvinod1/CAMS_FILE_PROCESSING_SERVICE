# Phase 0: Research — Epic 6: Cloud Portability via Interface + Adapter

**Purpose**: Resolve unknowns identified during planning before starting design.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## Research Tasks

### 1. MinIO + AWS SDK v2 as a Local GCS Replacement

**Question**: Can the AWS S3 SDK v2 be pointed at MinIO to provide a GCS-compatible local adapter without a GCS emulator?

**Findings**:
- MinIO implements the AWS S3 API completely. The AWS SDK v2 `S3Client` can be configured with a custom endpoint (`http://localhost:9000`) and path-style access (`forcePathStyle=true`).
- Testcontainers provides `MinioContainer` in the `org.testcontainers:minio` module — no manual Docker setup required in tests.
- Authentication: MinIO uses `accessKey` / `secretKey` (equivalent to AWS access key ID / secret). Set via `AwsBasicCredentials`.
- **Decision**: `LocalObjectStorageAdapter` uses AWS SDK v2 S3 client pointed at MinIO. No GCS emulator needed for local.
- **Rationale**: MinIO is production-grade, actively maintained, and fully S3-compatible. The GCS SDK has no equivalent local emulator with the same reliability.
- **Alternative rejected**: Using `fake-gcs-server` for local as well as GCP emulator tests — `fake-gcs-server` has incomplete API surface; MinIO is more reliable.

### 2. fake-gcs-server for GCP Adapter Contract Tests

**Question**: Which GCS emulator should be used in the `GcsObjectStorageContractTest`?

**Findings**:
- `fsouza/fake-gcs-server` (Docker image `fsouza/fake-gcs-server:latest`) implements the majority of the GCS XML and JSON API.
- It supports signed URL generation, object creation, retrieval, deletion, and bucket lifecycle rules.
- Configure `StorageOptions.newBuilder().setHost("http://localhost:4443").setProjectId("test-project")` to point the GCS SDK at the fake server.
- Known limitation: `fake-gcs-server` does not support GCS Pub/Sub notifications — use the Pub/Sub emulator separately.
- **Decision**: Use `GenericContainer<>("fsouza/fake-gcs-server:latest")` in `GcsObjectStorageContractTest`.
- **Rationale**: Most complete GCS emulator available; covers all `ObjectStoragePort` operations.

### 3. Spring Cloud GCP Pub/Sub vs Native Google Pub/Sub Client

**Question**: Should `PubSubMessageConsumer` use Spring Cloud GCP's `PubSubTemplate`, or the native Google Pub/Sub client library?

**Findings**:
- `PubSubTemplate` from `spring-cloud-gcp-pubsub` provides high-level `subscribeAndConvert()` with automatic message deserialization and acknowledgement handling.
- It integrates with Spring's `@Transactional` and supports async message processing out of the box.
- The native client is lower-level and requires manual ack/nack lifecycle management.
- The Pub/Sub emulator (`gcr.io/google.com/cloudsdktool/cloud-sdk:emulators`) supports the full Pub/Sub API and works with the standard Java client SDK.
- **Decision**: Use `PubSubTemplate.subscribeAndConvert()` in `PubSubMessageConsumer`.
- **Rationale**: Less boilerplate; consistent with the existing `PubSubMessagePublisher` pattern already in use for E2.

### 4. Formalising `FileMetadataRepository` as a Port Interface

**Question**: The existing `FileRecordRepository` extends Spring Data's `JpaRepository`. How do we extract a clean port interface without breaking existing code?

**Findings**:
- Create a new `FileMetadataRepository` interface in `com.cams.fileprocessing.interfaces` with only the methods the business layer actually uses.
- Rename the existing `FileRecordRepository` to `JpaFileMetadataRepository` and implement `FileMetadataRepository`.
- Update all injection sites to use `FileMetadataRepository` type instead of `FileRecordRepository`.
- The `SpannerFileMetadataRepository` can implement the same interface using Spring Data Spanner.
- This is a refactoring — no behaviour changes. All existing tests must still pass.
- **Decision**: Extract `FileMetadataRepository` port interface; rename existing repo; update injection sites.
- **Rationale**: Completes the constitution mandate. Without this, business code is coupled to JPA (a violation).
- **Risk**: Potentially wide blast radius across service/test classes. Mitigated by making it a dedicated PR with full test run.

### 5. Portability Test — Abstract Base Class Pattern

**Question**: What is the cleanest way to share an E2E scenario across two test configurations (local vs gcp-emulator)?

**Findings**:
- Define an abstract `PortabilityTest` base class with `@Test` methods and `protected abstract` container/config setup methods.
- `LocalProfilePortabilityTest extends PortabilityTest` starts Testcontainers for the local stack.
- `GcpEmulatorPortabilityTest extends PortabilityTest` starts Testcontainers for the GCP emulator stack.
- Spring `@SpringBootTest` with a `@TestConfiguration` that overrides beans based on the active profile handles dependency injection in both subclasses.
- **Decision**: Abstract base class with two concrete subclasses, both tagged `@Tag("portability")`.
- **Rationale**: Single scenario definition, dual execution. Adding a third profile (e.g. AWS) in future requires only a new subclass.
- **Alternative rejected**: Parameterised tests with `@ParameterizedTest` — harder to manage different Spring contexts per parameter.
