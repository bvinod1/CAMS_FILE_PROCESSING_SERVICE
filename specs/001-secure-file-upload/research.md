# Phase 0: Research - US-101: Secure File Upload Initiation

**Purpose**: Resolve unknowns identified during planning before starting design.
**Created**: 2026-03-08
**Plan**: [plan.md](plan.md)

No "NEEDS CLARIFICATION" items were identified in the Technical Context, as the project `constitution.md` provided a clear technology stack.

This phase will focus on confirming best practices for the selected technologies.

## Research Tasks

1.  **GCP Pre-Signed URL Generation with Spring Boot**
    -   **Task**: Investigate the best practices for generating GCS V4 pre-signed URLs using the `spring-cloud-gcp-starter-storage` library.
    -   **Goal**: Confirm the necessary IAM permissions and service account configuration required for the microservice to grant `storage.objects.create` permission to a client for a limited time. Document the core Java methods and classes involved.

2.  **Spanner Integration with Spring Data**
    -   **Task**: Research the optimal schema design for the `FileRecord` entity in Google Cloud Spanner, considering future query patterns (e.g., querying by status, flow type).
    -   **Goal**: Define the DDL for the `FileRecord` table, including primary key selection (e.g., `fileId` as a UUID string) and any secondary indexes. Confirm the correct Spring Data Spanner annotations (`@Table`, `@PrimaryKey`, `@Column`).

3.  **Local Development with Testcontainers**
    -   **Task**: Find best practices for setting up a local development environment for Spanner and Pub/Sub using Testcontainers.
    -   **Goal**: Provide a `docker-compose.yml` or Testcontainer configuration example for spinning up the Spanner emulator and Pub/Sub emulator. Document how to configure Spring Boot to connect to these emulators during integration tests.
