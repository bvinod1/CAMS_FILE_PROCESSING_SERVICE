# Phase 0: Research — Epic 8: Observability

**Purpose**: Resolve unknowns identified during planning before starting design.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## Research Tasks

### 1. logstash-logback-encoder — JSON Output in All Profiles

**Question**: What is the correct way to configure Logback JSON output that works in Spring Boot 3.x without conflicting with the default Spring Boot banner or the existing `logback-spring.xml`?

**Findings**:
- `net.logstash.logback:logstash-logback-encoder:7.x` is the standard choice — well-maintained, Spring Boot compatible.
- Use a `ConsoleAppender` with `LogstashEncoder` in `logback-spring.xml`. Remove the existing `PatternLayoutEncoder`.
- MDC fields are automatically included in the JSON output — no extra configuration needed.
- Spring Boot's banner (`Spring Boot ...`) is written to stdout before the Logback context is fully initialised. To suppress it: `spring.main.banner-mode=off` in `application.yml`.
- **Decision**: Replace `PatternLayoutEncoder` in `logback-spring.xml` with `LogstashEncoder`. Add `spring.main.banner-mode=off`.
- **Rationale**: Single Logback config for all profiles; MDC auto-included; standard library.
- **Alternative rejected**: Using `spring-boot-starter-logging` with `logging.structured.format.console=logstash` (Spring Boot 3.4+ feature) — requires Spring Boot 3.4, but we are on 3.2.3.

### 2. OpenTelemetry Java Agent — Version and Attachment

**Question**: Which version of the OTel Java agent should be used, and how is it attached in Docker without committing the binary to git?

**Findings**:
- OpenTelemetry Java Agent 2.x is compatible with Java 21 and Spring Boot 3.x.
- Download the agent as part of the Docker build: `RUN curl -L -o /app/otel-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.12.0/opentelemetry-javaagent.jar`
- Attach via `JAVA_TOOL_OPTIONS=-javaagent:/app/otel-javaagent.jar` environment variable — cleaner than modifying `ENTRYPOINT`.
- For local Maven test runs without Docker, the agent is not needed (logs still capture MDC fields; spans just aren't exported).
- **Decision**: Download agent in `Dockerfile`, attach via `JAVA_TOOL_OPTIONS`. Do not commit the jar to git (add to `.gitignore`).
- **Rationale**: Always-current agent version; no binary blobs in repo.
- **Alternative rejected**: Committing the jar — repo size bloat; violates standard practice.

### 3. Trace Context Propagation Through RabbitMQ

**Question**: The OTel Java agent auto-instruments many frameworks, but does it automatically propagate W3C `traceparent` through RabbitMQ messages?

**Findings**:
- The OTel agent includes auto-instrumentation for Spring AMQP. It automatically injects `traceparent` into AMQP message headers on the publisher side and extracts it on the consumer side.
- For Pub/Sub: the `spring-cloud-gcp-pubsub` auto-instrumentation is included in OTel agent 2.x — same behaviour.
- No manual header injection is needed as long as the OTel agent is attached.
- However, if the agent is NOT present (e.g. plain `mvn test` runs), trace context will not propagate. This is acceptable — local test runs do not send traces to Jaeger.
- **Decision**: Rely on OTel agent auto-instrumentation for RabbitMQ and Pub/Sub header propagation. No manual `traceparent` injection code needed.
- **Rationale**: Zero boilerplate; agent handles it.
- **Fallback**: If auto-instrumentation is insufficient for a specific library version, manually inject `traceparent` using `W3CTraceContextPropagator` as a fallback (T806 covers this).

### 4. Grafana Dashboard Provisioning Format

**Question**: What is the correct Grafana provisioning directory structure and file format to have dashboards auto-loaded on container start?

**Findings**:
- Grafana looks for datasource configs in `/etc/grafana/provisioning/datasources/` and dashboard configs in `/etc/grafana/provisioning/dashboards/`.
- A `dashboard-provider.yml` in the dashboards directory tells Grafana where to find the JSON files.
- Dashboard JSON files are exported directly from the Grafana UI ("Share → Export → Save to file"). They can be committed as-is.
- Mount the local `monitoring/grafana/provisioning/` directory to `/etc/grafana/provisioning/` in `docker-compose.yml`.
- **Decision**: Standard Grafana provisioning with volume mount. Dashboard JSON exported from Grafana UI and committed to `monitoring/grafana/provisioning/dashboards/`.
- **Rationale**: Reproducible dashboards; version-controlled; immediate availability on `docker-compose up`.

### 5. Prometheus Alert Routing — Alertmanager vs Log-Only

**Question**: For the MVP, do Prometheus alert rules need an Alertmanager to route to Slack/PagerDuty, or is log-based alerting sufficient?

**Findings**:
- Adding Alertmanager adds another container and routing configuration complexity.
- For MVP, Prometheus alert rules serve as the definition of what constitutes an alert condition — visible in `http://localhost:9090/alerts`.
- Alertmanager integration (Slack webhook, PagerDuty API key) is a one-line addition to `docker-compose.yml` and a simple `alertmanager.yml` config file.
- **Decision**: Define all alert rules in `alerting_rules.yml` and reference them from `prometheus.yml`. Leave Alertmanager integration as a documented follow-up step.
- **Rationale**: Alert rules as code is the primary deliverable. Routing to notification channels is an ops configuration concern, not a development one.
