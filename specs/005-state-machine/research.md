# Phase 0: Research — Epic 5: File Lifecycle State Machine

**Purpose**: Resolve unknowns identified during planning before starting design.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## Research Tasks

### 1. Spring State Machine 3.x — Stateless vs Stateful Machine

**Question**: Spring State Machine keeps machine state in memory by default. How do we make it stateless per call (reconstruct from persisted state each time)?

**Findings**:
- `StateMachineFactory<S, E>` creates new machine instances on demand — inject it rather than a singleton `StateMachine` bean.
- Call `StateMachineFactory.getStateMachine(fileId.toString())` to create a fresh instance.
- Use `StateMachineContext` to restore the machine to the current persisted state before sending an event.
- `StateMachinePersister` interface handles save/restore. Spring provides `DefaultStateMachinePersister`.
- For the local JPA profile: implement `StateMachinePersist<FileState, FileEvent, String>` backed by `FileMetadataRepository`.
- **Decision**: Use `StateMachineFactory` + custom `JpaStateMachinePersist` that reads the current `FileRecord.status` and restores machine state.
- **Rationale**: Stateless per call — no memory leak; consistent with the constitution mandate that state lives in the database.
- **Alternative rejected**: `@WithStateMachine` singleton scope — not thread-safe for concurrent file processing.

### 2. Atomic Audit INSERT with Status UPDATE

**Question**: How do we guarantee that the `file_status_audit` INSERT never happens without the `file_records` UPDATE committing, and vice versa?

**Findings**:
- Both operations must execute within the same Spring `@Transactional` boundary.
- `FileMetadataRepository.updateStatus()` is the only allowed entry point — declared `@Transactional(isolation = REPEATABLE_READ)`.
- If the audit INSERT fails, Spring rolls back the status UPDATE automatically.
- If the status UPDATE violates the `CHECK` constraint, the audit INSERT is also rolled back.
- **Decision**: Single `@Transactional` method in `FileMetadataRepository.updateStatus()` performing UPDATE then INSERT.
- **Rationale**: Database-level atomicity via ACID transaction — no application-level compensation needed.
- **Alternative rejected**: Two separate transactions with application-level compensation — risks inconsistency if the second transaction fails.

### 3. Invalid Transition Handling

**Question**: What should happen when an invalid transition is attempted — exception, silent ignore, or route to FAILED?

**Findings**:
- Spring State Machine throws no exception for an unaccepted event by default — it simply ignores it and calls the configured `StateMachineListener.eventNotAccepted()`.
- To make invalid transitions throw, configure `StateMachineConfigurer.withConfiguration().listener(...)` and throw from `eventNotAccepted`.
- Routing to `FAILED` on invalid transition is the safest policy for a financial platform — it surfaces the bug rather than silently continuing.
- **Decision**: Throw `InvalidStateTransitionException` from `eventNotAccepted` listener; catch it in `FileStateMachineService` and call the emergency `SYSTEM_ERROR` event to transition to `FAILED` with an audit reason of `"INVALID_STATE_TRANSITION"`.
- **Rationale**: Explicit failure is always safer than silent ignore for financial workflows.

### 4. Priority Queue — RabbitMQ x-max-priority vs Separate Queues

**Question**: Is it better to use one RabbitMQ queue with `x-max-priority` plugin, or three separate queues (cams.p0, cams.p1, cams.p2)?

**Findings**:
- RabbitMQ `x-max-priority` requires queueing messages with a `priority` property (0–255). The broker reorders messages in-queue accordingly.
- Separate queues with consumers checking P0 before P1 is simpler and more predictable. Consumer starvation for P2 is acceptable given the P2 24-hour SLA.
- Pub/Sub (GCP) does not support priority queuing natively — separate topics are the only option. Using separate queues locally keeps the topology consistent.
- **Decision**: Three separate queues: `cams.p0`, `cams.p1`, `cams.p2`. Workers check P0 first via `@RabbitListener` ordered consumers.
- **Rationale**: GCP-consistent topology; simpler debugging; no `x-max-priority` plugin required.
- **Alternative rejected**: Single queue with `x-max-priority` — inconsistent with GCP topology; harder to monitor per-priority depth.

### 5. DLQ Publishing — Transactional Outbox vs Fire-and-Forget

**Question**: Should DLQ publication use the transactional outbox pattern, or is fire-and-forget sufficient given the message is for observability/retry purposes?

**Findings**:
- For DLQ messages the message is not the source of truth — `file_records.status` is. If the DLQ publish fails, the file is still in `FAILED` state and visible via `GET /admin/dlq`.
- Full transactional outbox (write to `outbox` table, CDC or polling publisher) adds significant complexity.
- A simpler approach: publish DLQ message in `FileStateMachineService` immediately after the DB commit succeeds. On publish failure, log at ERROR level — the operations team can still see the file via the admin API.
- **Decision**: Fire-and-forget DLQ publish after DB commit. Log failure at ERROR level with full payload for manual recovery.
- **Rationale**: DB is source of truth; DLQ is a notification channel. Error logging + admin API provides adequate recovery without outbox complexity.
- **Alternative rejected**: Full transactional outbox — justified for primary event flow (e.g. `ScanCompletedEvent`) but disproportionate for a failure notification channel.
