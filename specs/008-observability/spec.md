# Epic 8: Observability

## Overview

Every component in the CAMS pipeline must be observable by default. This epic delivers structured JSON logging with trace IDs, Prometheus metrics for key business operations, OpenTelemetry distributed tracing across all workers, and pre-built Grafana dashboards. Observability is built into the code, not bolted on.

---

## User Stories

### US-801: Structured JSON Logging Across All Workers

**As a** platform engineer,
**I want** every log statement to include `fileId`, `flowType`, `status`, and `traceId` as structured fields,
**So that** I can search and correlate logs across all pipeline stages for a specific file.

**Acceptance Criteria:**

- AC-01: All log output is in JSON format (Logback JSON encoder) in all environments including local.
- AC-02: Every log statement that relates to a file includes `fileId` and `flowType` as MDC fields.
- AC-03: The OpenTelemetry trace ID is automatically injected into every log statement's JSON output.
- AC-04: PII fields (configurable per flow type via Epic 7) are replaced with `[REDACTED]` in all log output.
- AC-05: Log levels are configurable at runtime via `POST /actuator/loggers/{logger-name}` without restart.

### US-802: Prometheus Metrics for Business Operations

**As a** platform engineer,
**I want** key business metrics exposed as Prometheus counters and histograms,
**So that** I can alert on SLA breaches and identify pipeline bottlenecks from the Grafana dashboards.

**Acceptance Criteria:**

- AC-01: `cams_files_received_total{flowType, ingressChannel}` — counter incremented on every `FileReceivedEvent`.
- AC-02: `cams_scan_duration_seconds{result}` — histogram for ClamAV scan duration, labelled by CLEAN/INFECTED/ERROR.
- AC-03: `cams_validation_duration_seconds{result, flowType}` — histogram for validation duration.
- AC-04: `cams_processing_records_total{flowType, status}` — counter for records processed, labelled by SYNCED/FAILED.
- AC-05: `cams_external_api_duration_seconds{status_code}` — histogram for external API call latency.
- AC-06: `cams_queue_depth{queue, priority}` — gauge for message queue depths.
- AC-07: `clamav_signature_age_hours` — gauge for ClamAV signature database age (from Epic 2).
- AC-08: All metrics are exposed on `/actuator/prometheus` and scraped by the Prometheus container in `docker-compose.yml`.

### US-803: Distributed Tracing with OpenTelemetry

**As a** platform engineer,
**I want** a single trace to span the entire lifecycle of a file from ingress to completion,
**So that** I can identify exactly which step introduced latency or caused an error.

**Acceptance Criteria:**

- AC-01: The OpenTelemetry Java agent is added as a JVM argument in the Docker image.
- AC-02: Trace IDs are propagated through message queue headers (RabbitMQ / Pub/Sub).
- AC-03: Each pipeline stage (scan, validate, process) creates a child span of the originating trace.
- AC-04: Span attributes include `fileId`, `flowType`, `status`, `chunkSize` (for batch spans).
- AC-05: Local profile: traces are exported to a Jaeger container (`localhost:16686`).

### US-804: Pre-Built Grafana Dashboards

**As a** platform operator,
**I want** dashboards that show the real-time health of the file processing pipeline,
**So that** I can detect problems immediately without writing ad-hoc queries.

**Acceptance Criteria:**

- AC-01: **Pipeline Throughput** dashboard: files/min by ingress channel, scan pass/fail rate, validation error rate, processing records/sec.
- AC-02: **SLA Tracking** dashboard: P0 files in-flight, P0 queue depth, P0 time-in-pipeline vs. 30-min SLA threshold.
- AC-03: **External API Health** dashboard: call latency p50/p95/p99, error rate, circuit breaker state.
- AC-04: **ClamAV Health** dashboard: signature age, scan throughput, INFECTED rate.
- AC-05: All dashboards are provisioned as JSON files in `monitoring/grafana/provisioning/dashboards/`.

### US-805: Alerting Rules

**As a** platform operator,
**I want** automated alerts for conditions that indicate SLA risk or system degradation,
**So that** I am proactively notified before failures impact business outcomes.

**Acceptance Criteria:**

- AC-01: Alert: P0 file stuck in any non-terminal state for > 30 minutes.
- AC-02: Alert: ClamAV signature age > 24 hours.
- AC-03: Alert: DLQ depth > 0 for > 5 minutes.
- AC-04: Alert: External API circuit breaker open.
- AC-05: Alert: `cams_scan_duration_seconds` p95 > 30 seconds.
- AC-06: Alert rules defined as Prometheus `alerting_rules.yml` in `monitoring/`.

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-801 | MDC must be populated at the start of every event handler and cleared after processing. |
| FR-802 | Metrics must be registered programmatically via `MeterRegistry` — not via annotations that can be misconfigured. |
| FR-803 | Log statements must never interpolate variables directly into the message string — use SLF4J placeholders `{}` to avoid toString() on unhealthy objects. |
| FR-804 | No PII (account numbers, names, amounts) in any metric label value. |
