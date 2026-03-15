# Data Model — Epic 8: Observability

## Metrics Registry

All metrics are registered programmatically in the application startup via `MeterRegistry`. No metric labels contain PII.

### Counters

| Metric Name | Labels | Description |
|---|---|---|
| `cams_files_received_total` | `flowType`, `ingressChannel` | Incremented on `FileReceivedEvent` |
| `cams_files_failed_total` | `flowType`, `failedAtStatus` | Incremented when file enters terminal failure |
| `cams_processing_records_total` | `flowType`, `status` | Per-record outcome counter (`SYNCED`, `FAILED`, `SKIPPED`) |
| `cams_dlq_messages_total` | `flowType`, `reason` | DLQ message published counter |
| `cams_validation_errors_total` | `flowType`, `errorCode` | Per-column-error-code counter from E3 |

### Histograms (with p50, p95, p99 buckets)

| Metric Name | Labels | Buckets (seconds) | Description |
|---|---|---|---|
| `cams_scan_duration_seconds` | `result` | [0.1, 0.5, 1, 5, 15, 30, 60] | ClamAV scan duration |
| `cams_validation_duration_seconds` | `result`, `flowType` | [0.01, 0.05, 0.1, 0.5, 1, 2] | Header validation duration |
| `cams_processing_record_duration_seconds` | `flowType` | [0.01, 0.1, 0.5, 1, 2, 5] | Per-record external API call |
| `cams_http_request_duration_seconds` | `method`, `path`, `status` | [0.01, 0.05, 0.1, 0.5, 1, 5] | Inbound REST API latency |
| `cams_state_transition_duration_seconds` | `fromStatus`, `toStatus` | [0.001, 0.01, 0.05, 0.1] | State machine transition time |

### Gauges

| Metric Name | Labels | Description |
|---|---|---|
| `cams_queue_depth` | `queue`, `priority` | Current depth of each message queue |
| `cams_files_in_flight` | `status`, `flowType` | Count of files currently in each non-terminal state |
| `clamav_signature_age_hours` | — | Hours since ClamAV last updated signatures |

---

## Structured Log Schema

Every log line is a JSON object. The following fields are always present when a file context is active:

```json
{
  "timestamp":    "2024-01-15T10:30:00.123Z",
  "level":        "INFO",
  "logger":       "c.c.f.business.scan.ScanService",
  "message":      "Scan completed",
  "traceId":      "abc123def456",
  "spanId":       "789abc",
  "fileId":       "uuid",
  "flowType":     "NAV",
  "status":       "SCANNED_CLEAN",
  "ingressChannel": "REST",
  "priority":     "P0",
  "durationMs":   142,
  "thread":       "scan-worker-1"
}
```

**MDC fields set at event handler entry:**

| MDC Key | Value Source |
|---|---|
| `fileId` | `FileRecord.id` |
| `flowType` | `FileRecord.flowType` |
| `status` | Current `FileRecord.status` |
| `ingressChannel` | `FileRecord.ingressChannel` |
| `priority` | `FileRecord.priority` |

---

## OpenTelemetry Span Attributes

| Span Name | Attributes |
|---|---|
| `scan.execute` | `file.id`, `file.flowType`, `scan.result`, `scan.durationMs` |
| `validation.execute` | `file.id`, `file.flowType`, `validation.result`, `template.version` |
| `processing.batch` | `file.id`, `file.flowType`, `batch.chunkSize`, `batch.totalRecords` |
| `processing.record` | `file.id`, `record.rowNumber`, `api.url`, `api.statusCode` |
| `statemachine.transition` | `file.id`, `transition.from`, `transition.to`, `transition.actor` |

Trace context propagated via:
- RabbitMQ: `traceparent` and `tracestate` message headers
- Pub/Sub: `traceparent` and `tracestate` message attributes

---

## Grafana Dashboard Files

Location: `monitoring/grafana/provisioning/dashboards/`

| File | Dashboard Name | Key Panels |
|---|---|---|
| `pipeline-throughput.json` | Pipeline Throughput | Files/min by channel, scan pass rate, validation error rate, records/sec |
| `sla-tracking.json` | SLA Tracking | P0 queue depth, P0 in-flight age, SLA breach countdown |
| `external-api-health.json` | External API Health | Latency p50/p95/p99, error rate, circuit breaker state gauge |
| `clamav-health.json` | ClamAV Health | Signature age, scan throughput, INFECTED rate trend |
| `platform-overview.json` | Platform Overview | All four dashboards' KPIs on one page |

---

## Prometheus Alert Rules (`monitoring/alerting_rules.yml`)

```yaml
groups:
  - name: cams_sla
    rules:
      - alert: P0FileStuck
        expr: >
          (time() - cams_file_last_transition_timestamp{priority="P0"}) > 1800
          and cams_files_in_flight{priority="P0"} > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "P0 file stuck for > 30 minutes"

      - alert: ClamAvSignatureOutdated
        expr: clamav_signature_age_hours > 24
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "ClamAV signatures not updated in 24h"

      - alert: DLQDepthNonZero
        expr: cams_queue_depth{queue=~".*dlq.*"} > 0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Messages stuck in DLQ"

      - alert: CircuitBreakerOpen
        expr: resilience4j_circuitbreaker_state{state="open"} == 1
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Circuit breaker OPEN — external API degraded"
```

---

## Docker Compose Additions (monitoring stack)

```yaml
  prometheus:
    image: prom/prometheus:v2.50.1
    ports: ["9090:9090"]
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:10.4.0
    ports: ["3000:3000"]
    volumes:
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning

  jaeger:
    image: jaegertracing/all-in-one:1.56
    ports:
      - "16686:16686"   # Jaeger UI
      - "4317:4317"     # OTLP gRPC
      - "4318:4318"     # OTLP HTTP
```
