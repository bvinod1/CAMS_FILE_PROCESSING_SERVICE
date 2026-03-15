# Phase 0: Research — Epic 4: Chunked Record Processing

**Purpose**: Resolve unknowns identified during planning before starting design.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## Research Tasks

### 1. Spring Batch 5 Partitioned Processing on a Single JVM

**Question**: How does Spring Batch 5 partitioned step work, and is a remote partition manager needed or can partitions run on a single JVM thread pool?

**Findings**:
- Spring Batch 5 supports local partitioning via `TaskExecutorPartitionHandler` — no remote workers or separate process required.
- The `Partitioner` interface creates `ExecutionContext` maps (one per partition); the `TaskExecutorPartitionHandler` distributes them across a local `ThreadPoolTaskExecutor`.
- Each partition runs its own `ItemReader → ItemProcessor → ItemWriter` chain concurrently.
- Spring Batch 5 requires Java 17+ and is included in Spring Boot 3.x.
- **Decision**: Use `TaskExecutorPartitionHandler` with a thread pool sized from `ConfigurationPort.getProcessingConfig(flowType).threadPoolSize()`.
- **Rationale**: No additional infrastructure (Kafka, remote workers). Single JVM is sufficient for the 4-thread default with horizontal pod scaling in GKE.
- **Alternative rejected**: Remote partitioning via Spring Cloud Task — overkill; adds Kafka or another infrastructure dependency.

### 2. Spring Batch Job State in PostgreSQL

**Question**: Should Spring Batch manage its own schema, or should we customise the job repository?

**Findings**:
- Spring Batch automatically creates its schema (`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION`, etc.) when `spring.batch.jdbc.initialize-schema=always`.
- The Spring Batch tables use `BIGINT` primary keys — no conflict with our UUID-based tables.
- Job restart on crash: if the application restarts, `JobOperator.restart(jobExecutionId)` resumes from the last committed chunk.
- **Decision**: Use `spring.batch.jdbc.initialize-schema=always` in `application-local.yml`. Link our `processing_jobs` table to Spring Batch via `batch_job_execution_id` FK for cross-referencing.
- **Rationale**: Standard approach; no custom job repository needed.
- **Alternative rejected**: Custom in-memory `JobRepository` — loses crash recovery.

### 3. Resilience4j — Circuit Breaker vs Retry Placement

**Question**: Should Resilience4j annotations wrap the `ItemProcessor` method, the `ExternalApiPort` call, or a separate service?

**Findings**:
- Spring Batch `ItemProcessor.process()` is called per record. Wrapping it with `@Retry` and `@CircuitBreaker` works but mixes concerns.
- A cleaner pattern is a dedicated `ExternalApiCallService` that wraps the port call — the `ItemProcessor` delegates to it. The service method carries the Resilience4j annotations.
- The circuit breaker state is shared across all partitions (per-JVM Resilience4j registry) — if partition thread 1 trips the circuit, partition thread 2 will also see it OPEN.
- **Decision**: Create `ExternalApiCallService` with `@Retry` + `@CircuitBreaker` wrapping `ExternalApiPort.syncRecord()`. `ItemProcessor` calls `ExternalApiCallService`.
- **Rationale**: Single responsibility; Resilience4j annotations work reliably as Spring AOP proxies on a `@Service` bean.
- **Alternative rejected**: Manual Resilience4j decorator in adapter — ties resilience configuration to the infrastructure layer rather than business policy layer.

### 4. WireMock as a Testcontainers Container vs In-Process

**Question**: Should WireMock run as a Testcontainers Docker container or as an in-process `WireMockServer`?

**Findings**:
- In-process `WireMockServer` (library mode) is simpler and faster to start.
- Testcontainers `WireMockContainer` (`org.wiremock.integrations.testcontainers`) is available from WireMock 3.x; it runs WireMock as a Docker container — better production parity.
- For the component test we want the full stack in Docker, so the Testcontainers approach is consistent.
- **Decision**: Use `WireMockContainer` in component tests. Use in-process `WireMockServer` in unit tests for the `LocalExternalApiContractTest`.
- **Rationale**: Component test maximises production parity; contract test is fast via in-process mode.

### 5. PII Field Redaction Strategy

**Question**: Where should PII redaction happen — at the ItemProcessor, ItemWriter, or a shared service?

**Findings**:
- PII fields are defined per flow type in the validation template (Epic 3). The `ItemProcessor` has access to both the record payload and the flow type.
- Redaction must happen before: (a) logging the payload, (b) persisting `RecordResult.requestPayload`, (c) publishing any DLQ message containing the payload.
- A shared `PiiRedactionService` (pure domain logic, no infrastructure) that accepts a `Map<String, Object>` and a list of PII field names produces the redacted copy.
- **Decision**: `PiiRedactionService.redact(payload, piiFields) → Map<String, Object>` called in `ItemProcessor` before any log statement or port call.
- **Rationale**: Single, testable redaction boundary. The processor always works with a redacted copy — PII never reaches a log line or persistence layer.
- **Alternative rejected**: Redaction in the ItemWriter — too late; processor logs would already have exposed PII.
