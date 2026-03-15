# Quickstart: Epic 8 — Observability

**Purpose**: Guide for accessing logs, metrics, traces, and dashboards locally.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## 1. Prerequisites

- Java 21 (`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`)
- Maven 3.9+
- Docker Desktop (running)

## 2. Start the Full Observability Stack

```bash
docker-compose up -d
```

After all containers start (~60s), open the monitoring UIs:

| Service | URL | Credentials |
|---|---|---|
| Spring Boot Actuator | http://localhost:8080/actuator | — |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin / admin |
| Jaeger UI | http://localhost:16686 | — |

## 3. Verify Structured JSON Logging

Run the application and observe that logs are valid JSON:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn spring-boot:run -Dspring-boot.run.profiles=local 2>&1 | head -5 | python3 -m json.tool
# Should parse successfully — confirms JSON log format
```

Trigger an upload and filter logs by fileId:
```bash
# In another terminal
curl -s -X POST http://localhost:8080/api/v1/uploads \
  -H "Content-Type: application/json" \
  -d '{ "fileName": "nav.csv", "flowType": "NAV", "fileSizeBytes": 1024, "checksumMd5": "d41d8cd98f00b204e9800998ecf8427e" }' | jq .fileId
# Copy the fileId

# Filter logs for this file
docker-compose logs cams-app 2>&1 | python3 -c "
import sys, json
for line in sys.stdin:
    try:
        d = json.loads(line)
        if d.get('fileId') == 'YOUR_FILE_ID':
            print(json.dumps(d, indent=2))
    except: pass
"
```

## 4. Verify Prometheus Metrics

```bash
# Check all CAMS business metrics are present
curl -s http://localhost:8080/actuator/prometheus | grep '^cams_'
```

Expected metrics:
```
cams_files_received_total{flowType="NAV",ingressChannel="REST",...} 1.0
cams_scan_duration_seconds_count{result="CLEAN",...} 1.0
cams_files_in_flight{status="SCANNED_CLEAN",flowType="NAV",...} 1.0
```

In Prometheus UI (`http://localhost:9090`), try:
```
# Files received by flow type
cams_files_received_total

# Scan latency p95
histogram_quantile(0.95, rate(cams_scan_duration_seconds_bucket[5m]))
```

## 5. View Grafana Dashboards

1. Open `http://localhost:3000` (admin / admin)
2. Navigate to **Dashboards → Browse**
3. All 4 dashboards should be pre-loaded:
   - **Pipeline Throughput**
   - **SLA Tracking**
   - **ClamAV Health**
   - **Platform Overview**

Run some files through the pipeline to populate the panels with real data.

## 6. View Distributed Traces in Jaeger

After running a file through the full pipeline:

1. Open `http://localhost:16686`
2. Select service: `cams-file-processing`
3. Click **Find Traces**
4. Click any trace to see the full span waterfall: HTTP → scan → validate → process

Verify span attributes are present:
- `file.id`, `file.flowType` on all spans
- `scan.result` on scan spans
- `record.rowNumber`, `api.statusCode` on record processing spans

## 7. Verify Prometheus Alert Rules

```bash
open http://localhost:9090/alerts
```

All 5 alert rules should appear in `INACTIVE` state (no active breaches in a fresh dev environment):
- `P0FileStuck`
- `ClamAvSignatureOutdated`
- `DLQDepthNonZero`
- `CircuitBreakerOpen`
- `ScanLatencyHigh`

## 8. Test Log Level Runtime Change

```bash
# Change scan service to DEBUG at runtime
curl -s -X POST http://localhost:8080/actuator/loggers/com.cams.fileprocessing.business.scan \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'

# Trigger a scan and observe DEBUG lines in output
# Reset to INFO
curl -s -X POST http://localhost:8080/actuator/loggers/com.cams.fileprocessing.business.scan \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "INFO"}'
```

## 9. Run Tests

```bash
mvn test -Dtest=CamsMetricsTest,MdcContextUtilTest
```
