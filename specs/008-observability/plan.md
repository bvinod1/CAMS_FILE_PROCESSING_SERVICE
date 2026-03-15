# Implementation Plan: Epic 8 — Observability

**Branch**: `008-observability` | **Date**: 2026-03-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/008-observability/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

This plan delivers observability as a first-class feature across every CAMS pipeline stage. Structured JSON logging (Logback + logstash-logback-encoder) ensures every log line is machine-readable with MDC fields (`fileId`, `flowType`, `status`, `traceId`). Business metrics are registered programmatically via `MeterRegistry` (5 counters, 5 histograms, 3 gauges) and exposed to Prometheus. OpenTelemetry distributed tracing spans the full file lifecycle, with trace context propagated through message queue headers. Pre-built Grafana dashboards (4 panels) are provisioned as JSON files. Five Prometheus alerting rules cover SLA breaches, ClamAV staleness, and circuit breaker open conditions. PII fields are redacted from all log output per flow type configuration.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.2.3, Spring Boot Actuator, Micrometer (Prometheus registry), logstash-logback-encoder 7.x, OpenTelemetry Java Agent 2.x, Resilience4j Micrometer integration  
**Storage**: No new DB tables — metrics and traces are time-series data stored in Prometheus / Jaeger  
**Testing**: JUnit 5, AssertJ (MDC field assertions), `testcontainers-prometheus` (optional)  
**Target Platform**: GKE (gcp profile); Docker Compose (local profile) — Prometheus + Grafana + Jaeger containers  
**Project Type**: Cross-cutting observability layer  
**Performance Goals**: Metric recording overhead < 1 ms per operation; log JSON serialisation < 0.5 ms per line  
**Constraints**: No PII in any metric label value; no direct variable interpolation in log message strings; MDC always cleared after each worker invocation  
**Scale/Scope**: All 4 pipeline stages (scan, validate, process, state machine) plus all REST endpoints

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **[PASS] No PII in Logs or Metrics**: `PiiRedactionService` (E4) is called before any log statement in the processing pipeline. Metric label values use status codes and flow types — never account data.
- **[PASS] Observability Built In, Not Bolted On**: Metrics registered programmatically in `CamsMetrics` service at startup — no annotation-based metrics that can be silently misconfigured.
- **[PASS] Tracing Propagated**: Trace context flows through message queue headers — Jaeger shows a single root trace spanning multiple services.
- **[PASS] Dashboards as Code**: All Grafana dashboards are provisioned JSON files in the repository — no manual dashboard creation.
- **[PASS] Alerts as Code**: Prometheus alert rules defined in `monitoring/alerting_rules.yml` — version-controlled, reviewable.

## Project Structure

### Documentation (this feature)

```text
specs/008-observability/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/com/cams/fileprocessing/
│   │   └── observability/
│   │       ├── CamsMetrics.java          # programmatic MeterRegistry registrations
│   │       └── MdcContextUtil.java       # setFileContext / clearFileContext
│   └── resources/
│       └── logback-spring.xml            # JSON console appender (all profiles)
monitoring/
├── prometheus.yml                        # scrape config
├── alerting_rules.yml                    # 5 alert rules
└── grafana/
    └── provisioning/
        ├── datasources/
        │   └── prometheus.yml
        └── dashboards/
            ├── dashboard-provider.yml
            ├── pipeline-throughput.json
            ├── sla-tracking.json
            ├── clamav-health.json
            └── platform-overview.json
Dockerfile                                # includes OTel Java agent
```

```text
# (Additions to docker-compose.yml)
# prometheus, grafana, jaeger services documented in data-model.md
```

**Structure Decision**: Observability code lives in `observability/` package — a cross-cutting concern, not tied to any feature. Monitoring artifacts live in a top-level `monitoring/` directory for easy operator discoverability. Dashboards are JSON (not YAML) to match Grafana's native format.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *N/A*     | —          | —                                   |
