# CAMS File Processing Service — Platform Constitution

**Version**: 2.0.0 | **Ratified**: 2026-03-12 | **Last Amended**: 2026-03-12

---

## 1. Mission Statement

The CAMS File Processing Service is a cloud-portable, event-driven platform that ingests high-volume financial files (10,000 files/min, 1M+ records/file), scans them for malware, validates their structure, processes records in chunks, and provides real-time status tracking — all with a full, runnable local development environment that is the canonical starting point for every feature.

This constitution is the **source of truth** for all architectural decisions, technology choices, coding standards, testing mandates, and deployment topology. Every AI-generated code, PR review, and architectural recommendation must conform to it. Deviations require an explicit constitutional amendment, not a one-off exception.

---

## 2. Prime Directive: Program to the Interface, Not the Implementation

> **Every piece of infrastructure this system touches must be accessed through a Java interface defined in the `interfaces` package. No business logic class may ever import a cloud-vendor SDK class directly.**

### 2.1 The Three Laws

1. **Define a port interface** in `com.cams.fileprocessing.interfaces` for every infrastructure concern (storage, messaging, scanning, secrets, database).
2. **Provide a cloud implementation** in `com.cams.fileprocessing.infrastructure.gcp` (or `aws`, etc.) that is activated by a Spring `@Profile`.
3. **Provide a mandatory local implementation** in `com.cams.fileprocessing.infrastructure.local` that is backed by Docker containers (MinIO, RabbitMQ, ClamAV, PostgreSQL) and activated by `@Profile("local")`.

### 2.2 Law Enforcement — ArchUnit

The following ArchUnit rule is non-negotiable and must pass on every build:

```java
@ArchTest
static final ArchRule businessLayerMustNotDependOnInfrastructure =
    noClasses()
        .that().resideInAPackage("..business..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..infrastructure..", "com.google.cloud..", "software.amazon..");

@ArchTest
static final ArchRule testsMustNotUseMocks =
    noClasses()
        .that().resideInAPackage("..test..")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName(org.mockito.Mockito.class.getName());
```

### 2.3 Interface Table

| Interface | Local Adapter (Docker) | GCP Adapter | AWS Adapter (future) |
|---|---|---|---|
| `ObjectStoragePort` | MinIO (`minio:9000`) | Google Cloud Storage | Amazon S3 |
| `MessagePublisherPort` | RabbitMQ (`rabbitmq:5672`) | GCP Pub/Sub | Amazon SQS |
| `MessageConsumerPort` | RabbitMQ (`rabbitmq:5672`) | GCP Pub/Sub | Amazon SQS |
| `VirusScanPort` | ClamAV (`clamav:3310`) | ClamAV sidecar on GKE | ClamAV sidecar on EKS |
| `FileMetadataRepository` | PostgreSQL (`postgres:5432`) | Cloud Spanner | Amazon Aurora |
| `SecretPort` | Environment variables / `.env` | GCP Secret Manager | AWS Secrets Manager |
| `SignedUrlPort` | Pre-signed MinIO URL | GCS V4 Signed URL | S3 Pre-signed URL |

### 2.4 Local Adapter Structure

```
infrastructure/local/
  LocalObjectStorageAdapter.java    // MinIO via AWS SDK v2 (compatible)
  LocalMessagePublisherAdapter.java // RabbitMQ via Spring AMQP
  LocalMessageConsumerAdapter.java  // RabbitMQ via Spring AMQP
  LocalVirusScanAdapter.java        // ClamD socket connection
  LocalFileMetadataRepository.java  // Spring Data JPA + PostgreSQL
  LocalSecretAdapter.java           // Environment variable resolution
  LocalSignedUrlAdapter.java        // MinIO pre-signed URL generation
```

---

## 3. Package Structure

```
com.cams.fileprocessing/
  interfaces/               # ALL port interfaces — no implementations here
    ObjectStoragePort.java
    MessagePublisherPort.java
    MessageConsumerPort.java
    VirusScanPort.java
    SecretPort.java
    SignedUrlPort.java

  business/                 # Pure domain logic — ZERO infrastructure imports
    upload/
    scan/
    validation/
    processing/
    tracking/

  infrastructure/           # All adapters — activated by @Profile
    gcp/
      GcsObjectStorageAdapter.java
      PubSubMessagePublisherAdapter.java
      SpannerFileMetadataRepository.java
      GcsSignedUrlAdapter.java
    aws/                    # Future
    local/
      LocalObjectStorageAdapter.java
      LocalMessagePublisherAdapter.java
      LocalFileMetadataRepository.java
      LocalSignedUrlAdapter.java
      LocalVirusScanAdapter.java

  features/                 # Vertical slices per Epic
    upload/
    scan/
    validation/
    processing/
    tracking/
    ui/

  common/                   # Shared: exceptions, utils, config, state machine
    GlobalExceptionHandler.java
    FileStatus.java
    FileStatusMachine.java
```

---

## 4. The 9 Epics

| Epic | Name | Key User Stories |
|---|---|---|
| **E1** | Multi-Source Ingress Gateways | REST upload, SFTP ingest, GCS bucket trigger, unified `FileReceivedEvent` |
| **E2** | Malware Scanning | ClamAV scan, quarantine/clean bucket routing, scan result event |
| **E3** | Header Validation | Dynamic template matching, field-level error reporting, validation event |
| **E4** | Chunked Record Processing | Spring Batch partitioned processing, external API call per record, record-level status |
| **E5** | State Machine | Full FSM lifecycle, `FileStatus` transitions, Spanner persistence, DLQ handling |
| **E6** | Cloud-Portability | Interface + adapter pattern enforced, local stack complete, profile switching |
| **E7** | Config Management | Dynamic validation templates, feature flags, GCP Config / env-based local |
| **E8** | Observability | Structured JSON logging, distributed tracing, Prometheus metrics, Grafana dashboards |
| **E9** | React Status UI | Real-time tracker, file/record drill-down, priority queue status |

---

## 5. Core Principles

### P1 — Event-Driven First
All long-running processes are decoupled via message queue. The API never blocks on scanning, validation, or record processing. Every worker communicates exclusively through published events.

### P2 — Cloud-Portable by Abstraction (NOT Multi-Cloud)
The system targets **one cloud at a time**, selected at deployment. Portability is achieved through the interface + adapter pattern (see §2). Switching clouds means changing the active Spring `@Profile` — not rewriting business logic. ArchUnit enforces this boundary on every build.

### P3 — Interface-First, Always
No class in `business/` or `features/` may import a cloud-vendor class. All infrastructure access is via a port interface. This is enforced by ArchUnit (see §2.2).

### P4 — Local-First Development
Every feature must run end-to-end on a developer laptop with `docker-compose up -d`. The `local` Spring profile is the **primary** development target. Cloud deployment is a promotion, not the development environment.

> **Golden Rule: If it cannot run on your laptop with `docker-compose up`, it is not ready for the cloud.**

### P5 — No Mocks, Ever
Mockito is banned. H2 is banned. MockMvc is banned. All tests use real components via Testcontainers. See §12 for the full testing mandate.

### P6 — Asynchronous Processing is Mandatory
No synchronous blocking I/O in processing workers. Pre-signed URLs offload file bytes directly to storage. Workers process events from queues.

### P7 — Idempotency and At-Least-Once Safety
Every worker must be idempotent — processing the same message twice must produce the same result. State transitions are persisted to the database before emitting downstream events (transactional outbox pattern).

### P8 — Spec-Driven Development
The OpenAPI contract (`openapi.yaml`) is amended before implementation begins. No API endpoint may exist without a corresponding spec entry.

### P9 — Immutable Audit Trail
Every file and record state transition is persisted with a timestamp, actor, and reason. Audit records are never deleted. Retention: 7 years.

### P10 — Observability Built-In
Every service must emit: structured JSON logs (SLF4J + Logback), distributed trace IDs (OpenTelemetry), and Prometheus metrics for key business operations (files/min, records/sec, scan latency, validation error rate).

---

## 6. Approved Technology Stack

### 6.1 Invariant Layer (Never Changes)

| Concern | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.x, Spring WebFlux |
| Batch | Spring Batch 5.x |
| State Machine | Spring State Machine 4.x |
| Validation | Jakarta Bean Validation + Hibernate Validator |
| Logging | SLF4J + Logback (JSON encoder) |
| Metrics | Micrometer + Prometheus |
| Tracing | OpenTelemetry Java agent |
| Testing | JUnit 5, Testcontainers, ArchUnit, WireMock |
| API Spec | OpenAPI 3.0 + springdoc-openapi |
| Build | Maven 3.9.x |
| Containers | Docker + docker-compose |

### 6.2 Switchable Layer (Profile-Driven)

| Concern | `local` Profile | `gcp` Profile | `aws` Profile (future) |
|---|---|---|---|
| Object Storage | MinIO (Docker) | Google Cloud Storage | Amazon S3 |
| Message Queue | RabbitMQ (Docker) | GCP Pub/Sub | Amazon SQS |
| Database | PostgreSQL (Docker) | Cloud Spanner | Amazon Aurora PostgreSQL |
| Virus Scan | ClamAV (Docker) | ClamAV sidecar | ClamAV sidecar |
| Secrets | `.env` / env vars | GCP Secret Manager | AWS Secrets Manager |
| Config | `application-local.yml` | `application-gcp.yml` | `application-aws.yml` |

### 6.3 Local Development Stack (`docker-compose.yml`)

The canonical `docker-compose.yml` at repository root must define:

```yaml
version: "3.9"
services:
  rabbitmq:
    image: rabbitmq:3.13-management
    ports: ["5672:5672", "15672:15672"]
    environment:
      RABBITMQ_DEFAULT_USER: cams
      RABBITMQ_DEFAULT_PASS: cams
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      retries: 5

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    ports: ["9000:9000", "9001:9001"]
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 10s
      retries: 5

  postgres:
    image: postgres:16-alpine
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: cams
      POSTGRES_USER: cams
      POSTGRES_PASSWORD: cams
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U cams"]
      interval: 10s
      retries: 5

  clamav:
    image: clamav/clamav:stable
    ports: ["3310:3310"]
    healthcheck:
      test: ["CMD", "clamdcheck.sh"]
      interval: 30s
      retries: 5

  wiremock:
    image: wiremock/wiremock:3.x
    ports: ["8089:8080"]
    volumes: ["./wiremock:/home/wiremock"]

  sftp:
    image: atmoz/sftp:latest
    ports: ["2222:22"]
    command: cams:cams:::upload

  prometheus:
    image: prom/prometheus:latest
    ports: ["9090:9090"]
    volumes: ["./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml"]

  grafana:
    image: grafana/grafana:latest
    ports: ["3000:3000"]
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes: ["./monitoring/grafana:/var/lib/grafana"]

  loki:
    image: grafana/loki:latest
    ports: ["3100:3100"]
```

### 6.4 `application-local.yml`

```yaml
spring:
  profiles:
    active: local

  datasource:
    url: jdbc:postgresql://localhost:5432/cams
    username: cams
    password: cams
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    database-platform: org.hibernate.dialect.PostgreSQLDialect

  rabbitmq:
    host: localhost
    port: 5672
    username: cams
    password: cams

cams:
  storage:
    endpoint: http://localhost:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket-quarantine: cams-quarantine
    bucket-clean: cams-clean
    bucket-failed: cams-failed
  scan:
    host: localhost
    port: 3310
  external-api:
    base-url: http://localhost:8089
  secrets:
    source: env
```

---

## 7. Data Model

### 7.1 `file_records` Table

| Column | Type | Notes |
|---|---|---|
| `file_id` | `VARCHAR(36)` PK | UUID string |
| `original_file_name` | `VARCHAR(512)` | |
| `flow_type` | `VARCHAR(64)` | e.g., `NAV`, `TRANSACTION` |
| `ingress_channel` | `VARCHAR(32)` | `REST`, `SFTP`, `GCS_TRIGGER` |
| `checksum_md5` | `CHAR(32)` | Client-supplied MD5 |
| `checksum_sha256` | `CHAR(64)` | System-computed post-upload |
| `status` | `VARCHAR(32)` | See §8 |
| `priority` | `SMALLINT` | 0=highest |
| `gcs_bucket` | `VARCHAR(256)` | |
| `gcs_object_path` | `VARCHAR(1024)` | |
| `created_at` | `TIMESTAMPTZ` | |
| `updated_at` | `TIMESTAMPTZ` | |
| `scanned_at` | `TIMESTAMPTZ` | |
| `validated_at` | `TIMESTAMPTZ` | |
| `completed_at` | `TIMESTAMPTZ` | |
| `error_message` | `TEXT` | Last terminal error |

### 7.2 `file_status_audit` Table (Immutable)

| Column | Type | Notes |
|---|---|---|
| `audit_id` | `VARCHAR(36)` PK | |
| `file_id` | `VARCHAR(36)` FK | |
| `from_status` | `VARCHAR(32)` | |
| `to_status` | `VARCHAR(32)` | |
| `actor` | `VARCHAR(128)` | Service name |
| `reason` | `TEXT` | |
| `transitioned_at` | `TIMESTAMPTZ` | |

### 7.3 `FileReceivedEvent` (Pub/Sub / RabbitMQ)

```json
{
  "eventId": "uuid",
  "eventType": "FILE_RECEIVED",
  "fileId": "uuid",
  "originalFileName": "nav_20260312.csv",
  "flowType": "NAV",
  "ingressChannel": "REST",
  "checksumMd5": "d41d8cd98f00b204e9800998ecf8427e",
  "gcsBucket": "cams-quarantine",
  "gcsObjectPath": "uploads/2026/03/12/uuid.csv",
  "priority": 0,
  "occurredAt": "2026-03-12T10:00:00Z"
}
```

---

## 8. File State Machine

```
AWAITING_UPLOAD
    │  (pre-signed URL issued; client uploads directly to storage)
    ▼
UPLOADED
    │  (FileReceivedEvent published to queue)
    ▼
SCANNING
    │  (ClamAV scanning in progress)
    ├──[CLEAN]──► SCANNED_CLEAN
    │                   │
    │                   ▼
    │              VALIDATING
    │                   │
    │         ┌────[PASS]────┐
    │         ▼              ▼
    │     VALIDATED        VALIDATION_FAILED ──► DLQ
    │         │
    │         ▼
    │     PROCESSING
    │         │
    │    [complete]──► COMPLETED
    │    [partial] ──► PARTIALLY_COMPLETED
    │
    └──[INFECTED]──► SCAN_FAILED ──► DLQ

    Any state ──[unrecoverable error]──► FAILED ──► DLQ
```

Transitions are persisted to `file_status_audit` before the next event is published (transactional outbox).

---

## 9. Non-Functional Requirements

| Requirement | Target |
|---|---|
| API response time (p99) | < 200 ms |
| Files ingested per minute | 10,000 |
| Records per file | Up to 1,000,000 |
| E2E processing time for 1M-record file (p95) | < 2 hours |
| Audit log retention | 7 years |
| Availability | 99.9% |
| Recovery Point Objective (RPO) | < 1 minute |
| Recovery Time Objective (RTO) | < 5 minutes |
| Security | All files scanned; PII redacted from logs; checksums verified at upload + post-scan |
| Fairness | Priority queues; P0 (NAV) never starved |

---

## 10. API Surface

All APIs follow the contract in `specs/*/contracts/openapi.yaml`. No endpoint exists without a spec entry.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/uploads` | Request pre-signed upload URL |
| `POST` | `/api/v1/uploads/{fileId}/confirm` | Confirm upload complete |
| `GET` | `/api/v1/uploads/{fileId}/status` | Poll file status |
| `GET` | `/api/v1/uploads/{fileId}/records` | Get record-level status |
| `POST` | `/api/v1/sftp/ingest` | SFTP ingest trigger (internal) |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/prometheus` | Metrics scrape endpoint |

---

## 11. Security Standards

- All HTTP endpoints require JWT authentication (OAuth2 Resource Server).
- Pre-signed URLs expire in **15 minutes**.
- All uploaded files are scanned before any processing begins.
- File checksums (MD5 client-supplied + SHA-256 system-computed) are verified and stored.
- PII fields must never appear in logs — use `[REDACTED]` masking.
- Secrets (credentials, API keys) are never hardcoded. Local: environment variables. GCP: Secret Manager.
- mTLS enforced between internal services in GKE (Istio sidecar).

---

## 12. Testing Mandate

### 12.1 The Absolute Bans

The following are **unconditionally prohibited** in this codebase:

| Banned Item | Reason |
|---|---|
| `Mockito.mock()` / `@Mock` / `@MockBean` | Hides real integration failures |
| `H2` in-memory database | Different SQL dialect from PostgreSQL/Spanner |
| `MockMvc` | Bypasses the real WebFlux filter chain |
| Wiremock for internal services | Internal services use real containers |
| `@SpringBootTest(webEnvironment=MOCK)` | Use `RANDOM_PORT` with real containers |

The ArchUnit rule in §2.2 enforces the Mockito ban at build time.

### 12.2 The Mandates

Every test assertion must be against **real components** started by Testcontainers:

| Infrastructure | Container |
|---|---|
| Database | `postgres:16-alpine` (local) or Spanner emulator (GCP profile) |
| Message Queue | `rabbitmq:3.13-management` (local) or Pub/Sub emulator (GCP profile) |
| Object Storage | `minio/minio:latest` (local) or `fake-gcs-server` (GCP profile) |
| Virus Scanner | `clamav/clamav:stable` |
| External HTTP APIs | WireMock (only for third-party APIs outside our control) |

### 12.3 Test Layers

**Layer 1 — Component Tests** (`src/test/java/.../component/`)
- Scope: One microservice feature in full isolation — real containers, no network calls to other services.
- Annotation: `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- Example: `UploadComponentTest` — starts `postgres` + `minio` + `rabbitmq` containers, calls the REST API, asserts DB and queue state.

**Layer 2 — Integration Tests** (`src/test/java/.../integration/`)
- Scope: Across two or more features/services via real message queue.
- Example: `UploadToScanIntegrationTest` — publishes `FileReceivedEvent` to RabbitMQ, verifies `ScanService` consumes it and updates DB status to `SCANNING`.

**Layer 3 — Architecture Tests** (`src/test/java/.../arch/`)
- Scope: ArchUnit rules as described in §2.2.
- Must pass with zero violations on every build.

**Layer 4 — Contract Tests** (`src/test/java/.../contract/`)
- Scope: Verify every adapter implementation against its port interface using a shared abstract base class.
- Pattern:

```java
// Base class shared by all adapter implementations:
public abstract class ObjectStorageContractTest {
    protected abstract ObjectStoragePort adapter();

    @Test void shouldStoreAndRetrieveObject() { ... }
    @Test void shouldGenerateValidSignedUrl() { ... }
    @Test void shouldDeleteObject() { ... }
}

// Local adapter contract:
class LocalObjectStorageContractTest extends ObjectStorageContractTest {
    @Container static MinIOContainer minio = new MinIOContainer("minio/minio:latest");
    @Override protected ObjectStoragePort adapter() { return new LocalObjectStorageAdapter(minio.getS3URL(), ...); }
}

// GCP adapter contract:
class GcsObjectStorageContractTest extends ObjectStorageContractTest {
    @Container static FakeGcsContainer gcs = new FakeGcsContainer();
    @Override protected ObjectStoragePort adapter() { return new GcsObjectStorageAdapter(gcs.getEndpoint(), ...); }
}
```

**Layer 5 — Portability Tests** (`src/test/java/.../portability/`)
- Scope: Run the same business scenario end-to-end using the `local` profile and the `gcp` (emulator) profile.
- Both must produce identical results with respect to business outcomes. This is the definitive proof that the interface abstraction is correct.

### 12.4 Local-First Development Workflow

```bash
# Step 1: Start the full local stack
docker-compose up -d

# Step 2: Wait for all containers to be healthy
docker-compose ps

# Step 3: Run the application with the local profile
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Step 4: Run all tests against the local stack
mvn test -Dspring.profiles.active=local

# Step 5: Only when ALL tests pass locally, push to GitHub
git push origin main
```

### 12.5 Environment Parity Matrix

| Concern | Local | Dev | SIT | UAT | Prod (GCP) |
|---|---|---|---|---|---|
| Storage | MinIO | GCS | GCS | GCS | GCS |
| Queue | RabbitMQ | Pub/Sub | Pub/Sub | Pub/Sub | Pub/Sub |
| Database | PostgreSQL | Spanner | Spanner | Spanner | Spanner |
| Virus Scan | ClamAV Docker | ClamAV sidecar | ClamAV sidecar | ClamAV sidecar | ClamAV sidecar |
| Secrets | `.env` | Secret Manager | Secret Manager | Secret Manager | Secret Manager |
| Profile | `local` | `gcp` | `gcp` | `gcp` | `gcp` |
| Tests | Testcontainers | Testcontainers | Testcontainers | Testcontainers | N/A |

---

## 13. Deployment Topology

### 13.1 LOCAL — Primary Development Target

```
Developer Laptop
└── docker-compose up -d
    ├── RabbitMQ     :5672, :15672
    ├── MinIO        :9000, :9001
    ├── PostgreSQL   :5432
    ├── ClamAV       :3310
    ├── WireMock     :8089
    ├── SFTP         :2222
    ├── Prometheus   :9090
    ├── Grafana      :3000
    └── Loki         :3100
Spring Boot (profile=local) :8080
```

**The local stack must be the first thing that works for every feature. No exceptions.**

### 13.2 GCP — Production Target

```
GKE Cluster (Autopilot)
├── Ingress: HTTPS Load Balancer + Cloud Armor WAF
├── Services: Spring Boot pods (HPA min=2, max=20)
├── Messaging: GCP Pub/Sub topics + subscriptions
├── Storage: GCS buckets (quarantine / clean / failed)
├── Database: Cloud Spanner (multi-region)
├── Security: Workload Identity + Istio mTLS
├── Secrets: GCP Secret Manager
├── Observability: Cloud Logging + Cloud Trace + Prometheus + Grafana
└── CI/CD: Cloud Build + Artifact Registry
```

Profile: `gcp`

### 13.3 AWS — Future

Profile: `aws` — not yet active. Interface adapters for S3, SQS, Aurora PostgreSQL to be implemented in a future epic.

---

## 14. Definition of Done

A user story is **Done** when ALL of the following are true:

### 14.1 Code Quality
- [ ] All business logic resides in `business/` or `features/` packages with zero cloud-vendor imports
- [ ] Every new infrastructure concern has an interface in `interfaces/` package
- [ ] A GCP adapter AND a local adapter exist for every new interface
- [ ] No Mockito usage anywhere in the codebase (ArchUnit passes)
- [ ] All classes have Javadoc at the type level
- [ ] SLF4J structured logging at `INFO` (happy path) and `DEBUG` (diagnostic) levels

### 14.2 Testing
- [ ] Component test written using Testcontainers — covering happy path and at least two failure scenarios
- [ ] Contract test written as a subclass of the abstract base test class for every new adapter
- [ ] Architecture test (ArchUnit) passes with zero violations
- [ ] All tests are green: `mvn test` exits 0

### 14.3 Local Stack
- [ ] Feature runs end-to-end using `docker-compose up -d` + `mvn spring-boot:run -Dspring-boot.run.profiles=local`
- [ ] PR description includes a screenshot or log snippet of the feature working locally
- [ ] `docker-compose.yml` updated if new containers are required

### 14.4 API & Spec
- [ ] `openapi.yaml` updated before implementation (spec-first)
- [ ] `tasks.md` tasks marked complete
- [ ] API response matches the OpenAPI schema (validated by contract test)

### 14.5 Observability
- [ ] Structured log entries include `fileId`, `flowType`, `status`, and `traceId` where applicable
- [ ] At least one Prometheus counter or histogram registered for the new operation
- [ ] No PII in any log statement

### 14.6 Security
- [ ] New secrets use `SecretPort` — no hardcoded credentials
- [ ] Pre-signed URLs expire within the mandated window
- [ ] File integrity check present at the relevant processing stage

---

## 15. Governance

This constitution supersedes all prior architectural decisions documented in this repository. Any amendment requires:

1. A written rationale added to this document.
2. Update to the relevant ArchUnit tests.
3. Update to the `docker-compose.yml` if new local infrastructure is introduced.
4. Evidence that all existing tests still pass after the amendment.

**Version**: 2.0.0 | **Ratified**: 2026-03-12 | **Last Amended**: 2026-03-12
