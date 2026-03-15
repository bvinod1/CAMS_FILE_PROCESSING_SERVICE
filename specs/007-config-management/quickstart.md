# Quickstart: Epic 7 — Configuration Management

**Purpose**: Guide for using and testing the runtime configuration management API locally.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## 1. Prerequisites

- Java 21 (`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`)
- Maven 3.9+
- Docker Desktop (running)

## 2. Start the Local Stack

```bash
docker-compose up -d

# Verify config seed data loaded
docker exec -it cams-postgres psql -U cams -d cams \
  -c "SELECT flow_type, chunk_size, thread_pool_size FROM config_templates;"
# Expected: NAV (100, 8) and TRANSACTION (500, 4)

docker exec -it cams-postgres psql -U cams -d cams \
  -c "SELECT flow_type, scanning_enabled, validation_enabled, processing_enabled FROM feature_flags;"
# Expected: NAV and TRANSACTION both TRUE, TRUE, TRUE

docker exec -it cams-postgres psql -U cams -d cams \
  -c "SELECT flow_type, priority_level FROM priority_rules;"
# Expected: NAV=0 (P0), TRANSACTION=1 (P1)
```

## 3. Run the Application

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Admin credentials for local profile: `admin` / `admin` (configured in `application-local.yml`).

## 4. Explore the Config API

### Get current NAV processing config
```bash
curl -s -u admin:admin \
  http://localhost:8080/api/v1/admin/config/templates/NAV | jq .
```

### Update chunk size for TRANSACTION flows
```bash
curl -s -u admin:admin \
  -X PUT http://localhost:8080/api/v1/admin/config/templates/TRANSACTION \
  -H "Content-Type: application/json" \
  -d '{ "chunkSize": 250, "threadPoolSize": 6, "retryMaxAttempts": 3, "retryBackoffMs": [1000, 2000, 4000] }' | jq .
# Expected: version incremented to 2
```

### Check audit trail for the change
```bash
curl -s -u admin:admin \
  "http://localhost:8080/api/v1/admin/config/audit?flowType=TRANSACTION" | jq .
```

## 5. Test Feature Flags

### Disable scanning for TRANSACTION (bypass scan stage)
```bash
curl -s -u admin:admin \
  -X PUT http://localhost:8080/api/v1/admin/config/flags/TRANSACTION \
  -H "Content-Type: application/json" \
  -d '{ "scanningEnabled": false, "validationEnabled": true, "processingEnabled": true }' | jq .
```

Now upload a TRANSACTION file and observe in logs:
```
INFO  ScanService - Scanning bypassed by feature flag [flowType=TRANSACTION]
INFO  FileStateMachine - Transition [SCANNING → SCANNED_CLEAN] (flag bypass)
```

### Re-enable scanning
```bash
curl -s -u admin:admin \
  -X PUT http://localhost:8080/api/v1/admin/config/flags/TRANSACTION \
  -H "Content-Type: application/json" \
  -d '{ "scanningEnabled": true, "validationEnabled": true, "processingEnabled": true }' | jq .
```

## 6. Test Priority Rules

### Promote TRANSACTION to P0
```bash
curl -s -u admin:admin \
  -X PUT http://localhost:8080/api/v1/admin/config/priority \
  -H "Content-Type: application/json" \
  -d '{ "TRANSACTION": 0, "NAV": 0 }' | jq .
```

New TRANSACTION files will now be assigned P0 priority. Restart or wait for cache TTL (30s) to observe the change.

## 7. Test Cache Behaviour

```bash
# Make a GET request — cache miss (DB query logged at DEBUG)
curl -s -u admin:admin http://localhost:8080/api/v1/admin/config/templates/NAV > /dev/null

# Second request — cache hit (no DB query)
curl -s -u admin:admin http://localhost:8080/api/v1/admin/config/templates/NAV > /dev/null

# Check Prometheus metrics for cache stats
curl -s http://localhost:8080/actuator/prometheus | grep 'cache_gets'
# Expected: cache_gets_total{cache="processingConfigCache",result="hit"} = 1
```

## 8. Run Tests

```bash
# Controller tests (MockMvc)
mvn test -Dtest=ConfigAdminControllerTest

# Cache eviction unit test
mvn test -Dtest=CachingConfigurationServiceTest

# Integration test with Testcontainers PostgreSQL
mvn test -Dtest=PostgresConfigurationIntegrationTest
```
