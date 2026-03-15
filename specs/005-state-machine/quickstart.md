# Quickstart: Epic 5 — File Lifecycle State Machine

**Purpose**: Guide for running and testing the state machine and admin DLQ functionality locally.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## 1. Prerequisites

- Java 21 (`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`)
- Maven 3.9+
- Docker Desktop (running)

## 2. Start the Local Stack

```bash
docker-compose up -d
```

Verify the priority queues are declared in RabbitMQ:
```bash
open http://localhost:15672
# Login: guest / guest
# Expected: queues cams.p0, cams.p1, cams.p2, cams.dlq all visible
```

## 3. Run the Application

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 4. Inspect the Audit Trail

After running any file through the pipeline (E1–E4), check its full history:

```bash
curl -s http://localhost:8080/api/v1/uploads/{fileId}/history | jq .
```

Expected output shows every transition with timestamp and actor:
```json
{
  "fileId": "...",
  "history": [
    { "fromStatus": "PENDING_UPLOAD", "toStatus": "AWAITING_UPLOAD", "actor": "system", "transitionedAt": "..." },
    { "fromStatus": "AWAITING_UPLOAD", "toStatus": "UPLOADED",       "actor": "user@example.com", "transitionedAt": "..." },
    { "fromStatus": "UPLOADED",        "toStatus": "SCANNING",       "actor": "SCAN_WORKER", "transitionedAt": "..." },
    ...
  ]
}
```

## 5. Test an Invalid Transition (Negative Case)

You can directly provoke an invalid transition for testing via the admin retry endpoint with an invalid state:

Observe what happens in the logs when the state machine rejects an illegal event:
```
WARN  FileStateMachineService - Invalid transition attempted [fileId=... fromStatus=COMPLETED event=BEGIN_SCAN]
ERROR FileStateMachineService - Emergency fallback to FAILED [reason=INVALID_STATE_TRANSITION]
```

## 6. Test the DLQ and Manual Retry

### Step 1 — Force a file to SCAN_ERROR

Temporarily stop the ClamAV container to simulate a scan timeout:
```bash
docker-compose stop clamav
```

Upload and confirm a file — it will fail scanning after the timeout and transition to `SCAN_ERROR`.

### Step 2 — Check DLQ

```bash
curl -s http://localhost:8080/api/v1/admin/dlq | jq .
# Expected: the SCAN_ERROR file appears in the list
```

### Step 3 — Restart ClamAV and retry the file

```bash
docker-compose start clamav
# Wait for ClamAV to be ready (~30s)

curl -s -X POST http://localhost:8080/api/v1/admin/dlq/{fileId}/retry | jq .
# Expected: { "previousStatus": "SCAN_ERROR", "resetToStatus": "UPLOADED", "retryScheduledAt": "..." }
```

Watch the file re-enter the scan pipeline and reach `SCANNED_CLEAN`.

## 7. Test Priority Queue Ordering

Upload two files — one NAV (P0) and one TRANSACTION (P1) — simultaneously, with processing temporarily paused:

```bash
# Pause the worker by stopping the app, enqueue both, then restart
# When the app restarts, observe P0 file processed first in logs:
INFO  ProcessingWorker - Consuming from cams.p0 [fileId=NAV-file]
INFO  ProcessingWorker - cams.p0 empty, consuming from cams.p1 [fileId=TRANSACTION-file]
```

## 8. Run Tests

```bash
# Unit + architecture tests
mvn test '-Dgroups=!component'

# State machine integration test (Testcontainers PostgreSQL)
mvn test -Dtest=StateMachineIntegrationTest
```
