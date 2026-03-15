# Quickstart: Epic 4 — Chunked Record Processing

**Purpose**: Guide for running and testing the chunked record processing pipeline locally.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## 1. Prerequisites

- Java 21 (`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`)
- Maven 3.9+
- Docker Desktop (running)
- Epics 2 and 3 complete — processing is downstream of scan + validate

## 2. Start the Local Stack

```bash
docker-compose up -d
```

Verify Spring Batch tables were created:
```bash
docker exec -it cams-postgres psql -U cams -d cams -c "\dt BATCH_*"
# Expected: BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION, BATCH_STEP_EXECUTION, etc.
```

Verify processing config seed data:
```bash
docker exec -it cams-postgres psql -U cams -d cams \
  -c "SELECT flow_type, chunk_size, thread_pool_size FROM config_templates;"
# Expected: NAV (100, 8) and TRANSACTION (500, 4)
```

## 3. Run the Application

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 4. Test E2E: Upload → Scan → Validate → Process

### Step 1 — Create a test CSV (10 records)

```bash
cat > /tmp/nav-10records.csv << 'EOF'
ACCOUNT_ID,NAV_DATE,NAV_VALUE,CURRENCY,SOURCE_SYSTEM
550e8400-e29b-41d4-a716-446655440001,2024-01-15,1234.56,USD,BLOOMBERG
550e8400-e29b-41d4-a716-446655440002,2024-01-15,2345.67,USD,BLOOMBERG
550e8400-e29b-41d4-a716-446655440003,2024-01-15,3456.78,USD,BLOOMBERG
550e8400-e29b-41d4-a716-446655440004,2024-01-15,4567.89,USD,BLOOMBERG
550e8400-e29b-41d4-a716-446655440005,2024-01-15,5678.90,USD,BLOOMBERG
550e8400-e29b-41d4-a716-446655440006,2024-01-15,6789.01,USD,BLOOMBERG
550e8400-e29b-41d4-a716-446655440007,2024-01-15,7890.12,USD,BLOOMBERG
550e8400-e29b-41d4-a716-446655440008,2024-01-15,8901.23,USD,BLOOMBERG
550e8400-e29b-41d4-a716-446655440009,2024-01-15,9012.34,USD,BLOOMBERG
550e8400-e29b-41d4-a716-446655440010,2024-01-15,1234.56,USD,BLOOMBERG
EOF
```

### Step 2 — Upload, confirm, and wait for processing to complete

Follow the upload + scan + validate steps from the E1–E3 quickstarts. After `VALIDATED`, the `ProcessingWorker` consumes `ValidationCompletedEvent(PASS)` automatically.

Observe logs:
```
INFO  ProcessingWorker  - Received ValidationCompletedEvent [fileId=... result=PASS]
INFO  ProcessingBatch   - Starting batch job [fileId=... chunkSize=100 threads=8]
INFO  ProcessingBatch   - Partition 1 complete [synced=10 failed=0]
INFO  FileStateMachine  - Transition [PROCESSING → COMPLETED]
```

### Step 3 — Check file status

```bash
curl -s http://localhost:8080/api/v1/uploads/{fileId}/status | jq .
# Expected: { "status": "COMPLETED" }
```

### Step 4 — Check record results

```bash
curl -s "http://localhost:8080/api/v1/uploads/{fileId}/records?size=20" | jq .
# Expected: totalElements=10, syncedCount=10, failedCount=0
```

## 5. Test Partial Failure Scenario

Create a file with 2 records that have `SOURCE_SYSTEM=ERROR_TEST` (the WireMock error trigger):

```bash
cat > /tmp/nav-partial-fail.csv << 'EOF'
ACCOUNT_ID,NAV_DATE,NAV_VALUE,CURRENCY,SOURCE_SYSTEM
550e8400-e29b-41d4-a716-446655440001,2024-01-15,1234.56,USD,BLOOMBERG
550e8400-e29b-41d4-a716-446655440002,2024-01-15,2345.67,USD,ERROR_TEST
550e8400-e29b-41d4-a716-446655440003,2024-01-15,3456.78,USD,ERROR_TEST
EOF
```

Expected outcome:
- File transitions to `PARTIALLY_COMPLETED`
- `failedCount=2`, `syncedCount=1`

```bash
curl -s "http://localhost:8080/api/v1/uploads/{fileId}/records?status=FAILED" | jq .
# Shows 2 failed records with error details
```

## 6. Test Circuit Breaker

Activate the WireMock "all-fail" scenario:
```bash
curl -s -X POST http://localhost:8080/wiremock/__admin/scenarios/EXTERNAL_API/state \
  -H "Content-Type: application/json" \
  -d '{"desiredState": "ALL_ERROR"}'
```

Upload a file. After 5 consecutive failures you should see in the logs:
```
WARN  ExternalApiCallService - Circuit breaker OPEN — pausing batch
```

Reset:
```bash
curl -s -X POST http://localhost:8080/wiremock/__admin/scenarios/reset
```

## 7. Run Tests

```bash
# Fast tests
mvn test '-Dgroups=!component'

# Component test — full stack including WireMock
mvn test -Dgroups=component -Dtest=ProcessingPipelineComponentTest
```
