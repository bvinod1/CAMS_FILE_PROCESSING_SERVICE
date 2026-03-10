# Constitution for the CAMS File Processing Service

## 1. Mission Statement

This document outlines the foundational principles, architectural commitments, and technical standards for the CAMS File Processing Service. As the AI programming assistant, I will adhere to this constitution to ensure all generated code, advice, and modifications align with the project's strategic goals of scalability, reliability, security, and compliance.

## 2. Core Architectural Principles

1.  **Event-Driven First**: The system will be built on an event-driven, queue-based architecture. All long-running processes (scanning, validation, record processing) must be decoupled and triggered by asynchronous messages, preferably via GCP Pub/Sub. This is the primary pattern for ensuring scalability and resilience.
2.  **Asynchronous Processing is Mandatory**: No synchronous, blocking operations are permitted for file processing tasks. The initial file upload will be offloaded directly to GCS via pre-signed URLs. The API will respond immediately, and the workflow will proceed in the background.
3.  **Horizontal Scalability by Design**: All processing components (workers) must be stateless and designed to scale horizontally. We will leverage Google Kubernetes Engine (GKE) with Horizontal Pod Autoscaling (HPA) as the primary deployment target.
4.  **Reliability Through Idempotency and State**: Every process must be idempotent to support at-least-once message delivery. State transitions must be persisted transactionally to a durable database (Spanner) using the Spring State Machine pattern. Dead-letter queues (DLQs) are required for handling terminal failures.
5.  **Spec-Driven Development**: The API contract is king. Development will follow a spec-driven approach using OpenAPI. All API changes must begin with a modification to the `openapi.yaml` specification.

## 3. The Architectural Blueprint

I will follow the "Event-Driven Queue Architecture" as the canonical design.

-   **Ingress**: A REST API (`File Upload Service`) provides a pre-signed URL for direct GCS upload. It does not handle file streams. Upon client confirmation of upload, it publishes a `FILE_UPLOADED` event to Pub/Sub.
-   **Storage**: GCS is the source of truth for files, segregated into `quarantine`, `clean`, and `failed` buckets.
-   **Messaging**: GCP Pub/Sub is the central nervous system for orchestrating the workflow between microservices.
-   **Processing Workers**:
    1.  **Scan Service**: A dedicated worker (or Cloud Function) listens for new files, performs malware scanning, moves the file, and publishes the result.
    2.  **Validation Service**: A worker consumes `SCANNED_CLEAN` events, validates file headers against dynamic templates stored in the database, and publishes the result.
    3.  **Record Processing Service**: A worker consumes `VALIDATION_SUCCESS` events, uses Spring Batch to process records in partitioned chunks, calls the external synchronous endpoint, and tracks record-level status.
-   **State Management**:
    -   **File State**: A `FileStatus` entity will be managed by a Spring State Machine, with its state persisted in Cloud Spanner.
    -   **Record State**: A `RecordStatus` entity will track the status of individual records, also in Spanner, to support audit and reporting requirements.
-   **Frontend**: The React frontend will poll a `Tracker Service` API for file and record-level status updates.

## 4. Approved Technology Stack

I will generate code and provide solutions using only the following approved technologies:

| Layer      | Technology                               | Rationale                                    |
| :--------- | :--------------------------------------- | :------------------------------------------- |
| **Core**   | Spring Boot 3.x + WebFlux                | High-throughput, non-blocking I/O.           |
| **Queue**  | GCP Pub/Sub + Spring Cloud GCP           | Native, scalable, and integrated messaging.  |
| **Batch**  | Spring Batch                             | Robust, fault-tolerant chunk processing.     |
| **State**  | Spring State Machine + Cloud Spanner     | Transactional FSM persistence, strong consistency. |
| **Scan**   | ClamAV (Docker sidecar) or VirusTotal API | Integrable and standard security scanning.   |
| **Deploy** | GKE + Istio                              | Managed Kubernetes for autoscaling and mesh. |
| **Specs**  | OpenAPI 3.0 (SpecKit) + JUnit 5          | Contract-first development and testing.      |
| **Frontend**| React                                    | For the status dashboard UI.                 |

## 5. Non-Functional Requirements (Mandatory Checks)

I must ensure my suggestions and code adhere to these critical requirements:

-   **Security**: All uploads must be scanned for malware. PII must be redacted from logs. File integrity must be verified with checksums at key stages.
-   **Compliance**: An immutable audit trail for every file and record state transition must be maintained for 7 years. Data lineage must be traceable.
-   **Performance**: The end-to-end processing time for a 1M record file must target **< 2 hours (p95)**. API endpoints must respond in **< 200ms (p99)**.
-   **Fairness**: The system must support priority queues (e.g., P0 for NAV files) to prevent starvation and meet SLAs.
-   **Observability**: Code must include support for distributed tracing, structured logging (JSON), and custom metrics for monitoring key business operations (e.g., files/min, record sync rate).
-   **Reliability**: All external calls must be wrapped in a Circuit Breaker (Resilience4j) with exponential backoff and retry policies.

This constitution is the source of truth for my behavior on this project. Any deviation must be explicitly requested and justified.
