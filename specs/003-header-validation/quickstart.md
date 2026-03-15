# Quickstart: Epic 3 — Header Validation Pipeline

**Purpose**: Guide for running and testing the header validation pipeline locally.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## 1. Prerequisites

- Java 21 (`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`)
- Maven 3.9+
- Docker Desktop (running)
- Epic 2 (Malware Scanning) running — validation is downstream of scanning

## 2. Start the Local Stack

```bash
docker-compose up -d
```

Wait for all services to be healthy:
```bash
docker-compose ps
# All services should show "Up" or "healthy"
```

Check that the validation template seed data loaded:
```bash
docker exec -it cams-postgres psql -U cams -d cams -c "SELECT flow_type, version, active FROM validation_templates;"
# Expected: NAV and TRANSACTION rows
```

## 3. Run the Application

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 4. Test the Validation Pipeline End-to-End

### Step 1 — Upload and confirm a valid NAV file

Create a test CSV with the correct NAV header:
```bash
cat > /tmp/nav-valid.csv << 'EOF'
ACCOUNT_ID,NAV_DATE,NAV_VALUE,CURRENCY,SOURCE_SYSTEM
550e8400-e29b-41d4-a716-446655440000,2024-01-15,1234.56,USD,BLOOMBERG
EOF
```

Upload and confirm the file (follow Epic 1/2 quickstart for upload steps).  
After scanning completes, the validation worker picks up `ScanCompletedEvent(CLEAN)`.

### Step 2 — Observe validation

Watch the logs:
```
INFO  ValidationWorker - Received ScanCompletedEvent [fileId=... result=CLEAN]
INFO  ValidationService - Validating header [fileId=... flowType=NAV templateVersion=1]
INFO  ValidationService - Validation PASS [fileId=... errorCount=0 durationMs=45]
INFO  FileStateMachine - Transition [VALIDATING → VALIDATED]
```

### Step 3 — Check file status

```bash
curl -s http://localhost:8080/api/v1/uploads/{fileId}/status | jq .
# Expected: { "status": "VALIDATED" }
```

### Step 4 — Check validation errors endpoint (should be empty for valid file)

```bash
curl -s http://localhost:8080/api/v1/uploads/{fileId}/validation-errors | jq .
# Expected: { "result": "PASS", "errorCount": 0, "errors": [] }
```

## 5. Test with an Invalid File (Wrong Column Order)

```bash
cat > /tmp/nav-invalid.csv << 'EOF'
NAV_DATE,ACCOUNT_ID,NAV_VALUE,CURRENCY,SOURCE_SYSTEM
2024-01-15,550e8400-e29b-41d4-a716-446655440000,1234.56,USD,BLOOMBERG
EOF
```

Upload and confirm this file. Expected outcome:
- File transitions to `VALIDATION_FAILED`
- Validation errors endpoint returns `WRONG_POSITION` error for `ACCOUNT_ID` and `NAV_DATE`

```bash
curl -s http://localhost:8080/api/v1/uploads/{fileId}/validation-errors | jq .
# Expected: errors[] contains WRONG_POSITION entries
```

## 6. Test the Template Admin API

### Get current NAV template
```bash
curl -s http://localhost:8080/api/v1/validation-templates/NAV | jq .
```

### Update a template (add a new optional column)
```bash
curl -s -X PUT http://localhost:8080/api/v1/validation-templates/NAV \
  -H "Content-Type: application/json" \
  -d '{
    "columns": [
      { "name": "ACCOUNT_ID", "position": 0, "dataType": "UUID", "required": true, "allowNull": false },
      { "name": "NAV_DATE",   "position": 1, "dataType": "DATE", "required": true, "allowNull": false },
      { "name": "NAV_VALUE",  "position": 2, "dataType": "DECIMAL", "required": true, "allowNull": false },
      { "name": "CURRENCY",   "position": 3, "dataType": "STRING", "required": true, "allowNull": false },
      { "name": "SOURCE_SYSTEM", "position": 4, "dataType": "STRING", "required": true, "allowNull": false },
      { "name": "NOTES",      "position": 5, "dataType": "STRING", "required": false, "allowNull": true }
    ]
  }' | jq .
# Expected: version incremented to 2
```

## 7. Run Tests

```bash
# Unit + architecture tests (fast)
mvn test '-Dgroups=!component'

# Component test — validation pipeline with Testcontainers
mvn test -Dgroups=component -Dtest=ValidationPipelineComponentTest
```
