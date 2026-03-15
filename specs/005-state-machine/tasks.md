# Tasks — Epic 5: File Lifecycle State Machine

## T501 — Spring State Machine Dependency and Configuration Class

**Task:** Add Spring State Machine to pom.xml and create the base configuration.

**Subtasks:**
- T501-A: Add `spring-statemachine-core` and `spring-statemachine-data-jpa` to pom.xml
- T501-B: Create `FileStateMachineConfig extends StateMachineConfigurerAdapter<FileState, FileEvent>` in `com.cams.fileprocessing.features.statemachine`
- T501-C: Configure all states using `configureStates()` — define initial state (`PENDING_UPLOAD`) and terminal states (`COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, `QUARANTINED`, `SCAN_ERROR`, `VALIDATION_FAILED`)
- T501-D: Configure all transitions using `configureTransitions()` — add all 14 valid edges from `data-model.md`

**Acceptance:** Spring context loads with state machine bean; `mvn test` passes.

---

## T502 — FileState and FileEvent Enums

**Task:** Create the state and event enums used by the state machine.

**Subtasks:**
- T502-A: Create `FileState` enum mirroring all 14 values in `FileStatus` (verify 1:1 match)
- T502-B: Create `FileEvent` enum with all 14 events from `data-model.md`
- T502-C: Add a mapping utility `FileStateMapper.toStatus(FileState) → FileStatus` and reverse

**Acceptance:** All enum values align with `FileStatus` enum; mapper unit test passes (round-trip conversion).

---

## T503 — FileStateMachineService (Business Facade)

**Task:** Create the service that reconstructs a state machine from persisted state and executes transitions.

**Subtasks:**
- T503-A: Create `FileStateMachineService` injecting `StateMachineFactory` and `FileMetadataRepository`
- T503-B: Implement `transition(fileId, fileEvent, actor, reason)`:
  1. Load current `FileStatus` from DB
  2. Restore state machine to that state
  3. Send `fileEvent` — catch `InvalidStateTransitionException`
  4. Within a single transaction: update `file_records.status` + insert `file_status_audit` row
  5. Return `TransitionResult(previousStatus, newStatus, success, error)`
- T503-C: On `InvalidStateTransitionException`: log ERROR, transition to `FAILED` via emergency fallback, audit with reason `"INVALID_STATE_TRANSITION"`
- T503-D: Verify idempotency: calling the same transition twice is safe (second call is a no-op if already in target state)

**Acceptance:** Unit tests cover happy path, invalid transition, and idempotent call.

---

## T504 — Audit Trail Persistence (Transactional)

**Task:** Ensure every state machine transition writes an audit row atomically with the status update.

**Subtasks:**
- T504-A: Verify `FileMetadataRepository.updateStatus()` wraps both the `file_records` UPDATE and `file_status_audit` INSERT in `@Transactional`
- T504-B: Write integration test: simulate a commit failure mid-transaction — assert neither the status update nor the audit row is persisted (rolled back)
- T504-C: Verify `file_status_audit` has `REVOKE UPDATE, DELETE` in `db/init/01-schema.sql`

**Acceptance:** Transactional rollback test passes; REVOKE grant verified.

---

## T505 — Audit History API

**Task:** Expose the full audit trail for a file via REST.

**Subtasks:**
- T505-A: Add `GET /api/v1/uploads/{fileId}/history` to `UploadController`
- T505-B: Response: ordered list of `{ fromStatus, toStatus, actor, reason, transitionedAt }`
- T505-C: Returns `404` if no audit rows found for `fileId`
- T505-D: Add to `openapi.yaml`

**Acceptance:** Controller test passes; openapi.yaml validates.

---

## T506 — Replace All setStatus() Calls with State Machine

**Task:** Audit the entire codebase and replace any direct `fileRecord.setStatus()` calls with `FileStateMachineService.transition()`.

**Subtasks:**
- T506-A: `grep -r "setStatus\|\.status =" src/main/` — list all occurrences
- T506-B: Replace each occurrence with the appropriate `FileStateMachineService.transition(fileId, event, actor, reason)` call
- T506-C: Add ArchUnit rule: `noClasses().should().callMethod(FileRecord.class, "setStatus", FileStatus.class)`
- T506-D: Run `mvn test` — verify all 9+ unit tests still pass

**Acceptance:** Zero direct `setStatus` calls remain; ArchUnit rule passes.

---

## T507 — DLQ Publisher

**Task:** Implement automatic DLQ publishing when a file transitions to a terminal failure state.

**Subtasks:**
- T507-A: In `FileStateMachineService.transition()`: after committing a transition to a terminal failure state, call `MessagePublisherPort.publish("cams.dlq", FileFailedEvent)`
- T507-B: Create `FileFailedEvent` record in `com.cams.fileprocessing.features.statemachine`
- T507-C: DLQ message includes: `fileId`, `fromStatus`, `toStatus`, `errorMessage`, `actor`, `occurredAt`, audit trail length
- T507-D: Write unit test: mock `MessagePublisherPort`, trigger `SCAN_ERROR` transition, verify DLQ publish called once

**Acceptance:** DLQ unit test passes; no DLQ publish on successful transitions.

---

## T508 — DLQ Admin API (Manual Retry)

**Task:** Implement the admin endpoints for listing DLQ files and triggering manual retry.

**Subtasks:**
- T508-A: Create `AdminController` with `GET /api/v1/admin/dlq` — queries `file_records` for all terminal states
- T508-B: Implement `POST /api/v1/admin/dlq/{fileId}/retry`:
  1. Validate file is in a retry-eligible failure state
  2. Call `FileStateMachineService.transition(fileId, RESET_EVENT, "MANUAL_RETRY", reason)`
  3. Re-publish the trigger event for the target pipeline stage
- T508-C: Retry action is audit-logged with `actor = "MANUAL_RETRY"`
- T508-D: Add endpoints to `openapi.yaml`
- T508-E: Write controller tests for both endpoints

**Acceptance:** Controller tests pass; manual retry re-publishes correct event.

---

## T509 — Priority Queue Infrastructure

**Task:** Configure multi-priority queues in RabbitMQ (local) and document the Pub/Sub equivalent.

**Subtasks:**
- T509-A: Create `RabbitMqConfig` declaring exchanges and queues: `cams.p0`, `cams.p1`, `cams.p2` with DLX bindings
- T509-B: Configure worker consumers to check `cams.p0` before `cams.p1` (Spring AMQP `@RabbitListener` on ordered queues)
- T509-C: Set `FileRecord.priority` from `ConfigurationPort.getPriorityLevel()` at creation time in `UploadService`
- T509-D: Document Pub/Sub topic naming convention in `data-model.md` under Priority Queue Routing table

**Acceptance:** Integration test: P0 message consumed before P1 message even when P1 was published first.

---

## T510 — State Machine ArchUnit Tests

**Task:** Add ArchUnit rules to enforce state machine usage discipline.

**Subtasks:**
- T510-A: Rule: no class outside `statemachine.*` may call `FileStateMachineConfig` bean methods directly
- T510-B: Rule: `FileStateMachineService` is the only class permitted to call `FileMetadataRepository.updateStatus()`
- T510-C: Run all ArchUnit tests — verify zero violations

**Acceptance:** All ArchUnit rules pass with `mvn test`.
