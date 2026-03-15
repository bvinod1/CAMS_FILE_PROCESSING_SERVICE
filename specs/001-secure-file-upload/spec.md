# Feature Specification: US-101: Secure File Upload Initiation

**Feature Branch**: `001-secure-file-upload`
**Created**: 2026-03-08
**Status**: Draft
**Input**: User description: "US-101: Secure File Upload Initiation"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Securely Upload Large Financial Files (Priority: P1)

As a Portfolio Manager, I want to upload 1M+ record files via signed URLs so that files are securely received without size limits.

**Why this priority**: This is the foundational step for the entire file processing workflow. Without a secure and reliable upload mechanism, no other processing can occur. It directly impacts the core business function of ingesting financial data.

**Independent Test**: The upload process can be tested independently by verifying that a call to the API generates a valid pre-signed GCS URL. A file can then be uploaded to this URL using a standard HTTP client, and the system should correctly create an initial file record in the database with a status of `AWAITING_UPLOAD`, which can be queried via a status endpoint.

**Acceptance Scenarios**:

1.  **Given** a Portfolio Manager is authenticated,
    **When** they make an API call to request an upload URL, providing the file name, file type (e.g., "NAV"), and a checksum (MD5/SHA256),
    **Then** the system returns a unique `fileId` and a valid, time-limited (24-hour expiry) pre-signed URL for uploading the file directly to a secure, isolated GCS bucket.

2.  **Given** a valid pre-signed URL has been generated,
    **When** the user uploads a 4GB CSV file to that URL,
    **Then** the file is successfully stored in the designated GCS "quarantine" bucket, and the API that generated the URL responds in under 200ms (p99).

3.  **Given** a file has been successfully uploaded to the GCS bucket,
    **When** the system receives the notification of the new file,
    **Then** it transitions the corresponding file record in the database to `UPLOADED`.

### Edge Cases

-   **What happens when** an upload is attempted with an expired pre-signed URL? The system (GCS) should reject the upload with a 4xx error.
-   **What happens when** the provided checksum during the request does not match the checksum of the uploaded file? The file should be moved to a "failed" bucket with a status of `VALIDATION_FAILED` and a reason of "Checksum mismatch".
-   **How does the system handle** a request for an upload URL with a missing file name or flow type? The API should return a 400 Bad Request error with a descriptive message.
-   **What happens if** the file upload fails midway? The incomplete file part should be automatically cleaned up by GCS bucket lifecycle policies. The system will not know about this file, as the completion trigger is never fired.

## Requirements *(mandatory)*

### Functional Requirements

-   **FR-001**: The system MUST provide a secure API endpoint for clients to request a pre-signed URL for file uploads.
-   **FR-002**: The pre-signed URL MUST be generated for a specific object name in a designated GCS "quarantine" bucket.
-   **FR-003**: The pre-signed URL MUST have a fixed expiration time of **15 minutes** (per constitution §11 security mandate). The 24-hour value previously noted here was incorrect and has been removed.
-   **FR-004**: The system MUST create a preliminary file record in a persistent database (Spanner) when the upload URL is requested, including metadata like file name and `flow_type`.
-   **FR-005**: The system MUST support CSV and JSON file types up to 5GB.
-   **FR-006**: The initial API response for the pre-signed URL request MUST be faster than 200ms (p99).
-   **FR-007**: Raw uploaded files MUST be stored in an isolated GCS bucket with a Time-To-Live (TTL) policy for eventual cleanup.

### Key Entities

-   **FileRecord**: Represents a file being processed.
    -   **Attributes**: `fileId` (unique identifier), `originalFileName`, `flowType`, `checksum`, `status`, `createdAt`, `updatedAt`.
-   **UploadRequest**: Represents the client's request to upload a file.
    -   **Attributes**: `fileName`, `flowType`, `checksum`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

-   **SC-001**: 100% of file upload requests result in the generation of a valid, secure, pre-signed URL.
-   **SC-002**: The system successfully ingests files up to 5GB in size without data loss or corruption.
-   **SC-003**: The API endpoint for generating signed URLs maintains a p99 latency of less than 200ms under a load of 1000 concurrent requests.
-   **SC-004**: 99.99% of valid files uploaded to the pre-signed URL are successfully stored in the GCS quarantine bucket and have a corresponding record created in the database.

