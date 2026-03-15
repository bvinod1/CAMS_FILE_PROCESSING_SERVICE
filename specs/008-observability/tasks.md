# Tasks — Epic 8: Observability

## T801 — Logback JSON Encoder Setup

**Task:** Replace the default Logback console appender with a structured JSON encoder in all profiles.

**Subtasks:**
- T801-A: Add `logstash-logback-encoder` dependency to pom.xml
- T801-B: Create `src/main/resources/logback-spring.xml` with a `ConsoleAppender` using `LogstashEncoder`
- T801-C: Configure encoder to include MDC fields automatically in every log line
- T801-D: Verify output is valid JSON by running `mvn spring-boot:run` locally and inspecting stdout
- T801-E: Ensure no raw string interpolation of `fileId` or `flowType` variables — all must be MDC fields

**Acceptance:** Running the app produces valid JSON log lines with `traceId` field present.

---

## T802 — MDC Population in All Event Handlers

**Task:** Ensure every event handler and REST controller method populates MDC with file context fields.

**Subtasks:**
- T802-A: Create `MdcContextUtil` utility class with `setFileContext(FileRecord)` and `clearFileContext()` methods
- T802-B: Add `MdcContextUtil.setFileContext()` at the top and `clearFileContext()` in a `finally` block of every event handler: `ScanService`, `ValidationWorker`, `ProcessingWorker`, `FileStateMachineService`
- T802-C: Add filter or advice to `UploadController` that sets `fileId` in MDC for all `/{fileId}/*` path endpoints
- T802-D: Write test: for a given file operation, assert MDC map contains `fileId`, `flowType`, `status`

**Acceptance:** MDC fields present in log output; test passes.

---

## T803 — Prometheus Metrics — Counters and Histograms

**Task:** Register all business metrics via `MeterRegistry`.

**Subtasks:**
- T803-A: Create `CamsMetrics` service class in `com.cams.fileprocessing.observability` that accepts `MeterRegistry` in constructor
- T803-B: Register all 5 counters: `cams_files_received_total`, `cams_files_failed_total`, `cams_processing_records_total`, `cams_dlq_messages_total`, `cams_validation_errors_total`
- T803-C: Register all 5 histograms with correct label names and bucket boundaries (from `data-model.md`)
- T803-D: Register all 3 gauges: `cams_queue_depth`, `cams_files_in_flight`, `clamav_signature_age_hours`
- T803-E: Inject `CamsMetrics` into `ScanService`, `ValidationWorker`, `ProcessingWorker`, `FileStateMachineService` — increment at the correct points
- T803-F: Write unit test: verify `cams_files_received_total` increments by 1 after a `FileReceivedEvent` is processed

**Acceptance:** All registered metrics appear at `GET /actuator/prometheus`; counter test passes.

---

## T804 — Prometheus Scrape Config

**Task:** Configure Prometheus to scrape the application metrics endpoint.

**Subtasks:**
- T804-A: Add `io.micrometer:micrometer-registry-prometheus` to pom.xml (likely already present with Spring Boot Actuator)
- T804-B: Create `monitoring/prometheus.yml` with scrape config targeting `CAMS_FILE_PROCESSING_SERVICE:8080/actuator/prometheus`
- T804-C: Add Prometheus service to `docker-compose.yml` (already spec'd in E8 data-model.md — implement it)
- T804-D: Verify `http://localhost:9090/targets` shows the app as UP after `docker-compose up`

**Acceptance:** Prometheus UI shows the application target as `UP`.

---

## T805 — OpenTelemetry Agent Setup

**Task:** Add the OpenTelemetry Java agent to the Docker image and configure trace export to Jaeger.

**Subtasks:**
- T805-A: Download `opentelemetry-javaagent.jar` and add to `src/main/resources/agents/` (gitignored from binary commits — use Maven wrapper plugin or docker build ARG)
- T805-B: Update `Dockerfile` (create if not present): `ENTRYPOINT ["java", "-javaagent:/app/otel-javaagent.jar", ...]`
- T805-C: Set OTEL env vars: `OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317`, `OTEL_SERVICE_NAME=cams-file-processing`
- T805-D: Add Jaeger service to `docker-compose.yml` (from `data-model.md`)
- T805-E: Verify trace appears in Jaeger UI at `http://localhost:16686` after an upload request

**Acceptance:** Jaeger UI shows a trace for a file upload with at least 3 spans.

---

## T806 — Trace Context Propagation Through Message Queues

**Task:** Ensure the OpenTelemetry trace ID is propagated as a message header through RabbitMQ and Pub/Sub.

**Subtasks:**
- T806-A: In `LoggingMessagePublisher` (and future RabbitMQ/Pub/Sub publishers): inject `W3C traceparent` header from current OTel span context into every outbound message
- T806-B: In `RabbitMqMessageConsumer` (E6): extract `traceparent` from incoming message headers and restore the span context before invoking the handler
- T806-C: In `PubSubMessageConsumer` (E6): same for Pub/Sub message attributes
- T806-D: Write integration test: publish event with `traceparent` → assert handler receives same traceId

**Acceptance:** Trace propagation test passes; Jaeger shows a single root trace spanning scan + validate.

---

## T807 — Custom Span Attributes in Business Services

**Task:** Add custom span attributes to each pipeline service to enrich trace data.

**Subtasks:**
- T807-A: In `ScanService`: add span attributes `file.id`, `file.flowType`, `scan.result`, `scan.durationMs` using OTel API `Span.current().setAttribute(...)`
- T807-B: In `ValidationService`: add `file.id`, `file.flowType`, `validation.result`, `template.version`
- T807-C: In the Spring Batch `ItemProcessor`: add `file.id`, `record.rowNumber`, `api.statusCode`
- T807-D: In `FileStateMachineService`: add `transition.from`, `transition.to`, `transition.actor`

**Acceptance:** Jaeger trace detail view shows all custom attributes on each span.

---

## T808 — Grafana Dashboard Provisioning

**Task:** Create and provision Grafana dashboards as JSON files.

**Subtasks:**
- T808-A: Add Grafana service to `docker-compose.yml` with volume mount for provisioning directory
- T808-B: Create `monitoring/grafana/provisioning/datasources/prometheus.yml` pointing to `http://prometheus:9090`
- T808-C: Create `monitoring/grafana/provisioning/dashboards/pipeline-throughput.json` — panels: files/min by channel, scan pass rate, validation error rate, records/sec
- T808-D: Create `monitoring/grafana/provisioning/dashboards/sla-tracking.json` — panels: P0 in-flight, queue depth, age-in-pipeline
- T808-E: Create `monitoring/grafana/provisioning/dashboards/clamav-health.json` — panels: signature age, scan throughput, INFECTED rate
- T808-F: Verify dashboards load at `http://localhost:3000` after `docker-compose up`

**Acceptance:** All 3 dashboards visible in Grafana UI; panels display metrics data (even if zero).

---

## T809 — Prometheus Alerting Rules

**Task:** Create Prometheus alerting rules for SLA monitoring.

**Subtasks:**
- T809-A: Create `monitoring/alerting_rules.yml` with all 5 alert rules from `data-model.md`
- T809-B: Update `monitoring/prometheus.yml` to reference the alerting rules file
- T809-C: Verify alerts are visible in Prometheus under `Alerts` tab at `http://localhost:9090/alerts`

**Acceptance:** All 5 alert rules appear in Prometheus UI as `INACTIVE` (no breaches in dev).

---

## T810 — Log Level Runtime Configuration

**Task:** Enable runtime log level changes via Spring Boot Actuator.

**Subtasks:**
- T810-A: Verify `management.endpoints.web.exposure.include=health,info,prometheus,loggers` in `application.yml`
- T810-B: Test: `POST /actuator/loggers/com.cams.fileprocessing` with `{"configuredLevel": "DEBUG"}` → verify DEBUG logs appear
- T810-C: Verify default log level is `INFO`; no sensitive data logged at DEBUG level

**Acceptance:** Log level changes via Actuator take effect without restart.
