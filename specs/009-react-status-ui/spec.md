# Epic 9: React Status Dashboard

## Overview

A React web application provides portfolio managers, operations staff, and platform engineers with real-time visibility into the file processing pipeline. The dashboard shows file status, scan results, validation errors, per-record processing progress, and admin controls for DLQ management and manual retry.

---

## User Stories

### US-901: File Tracker — Pipeline Overview

**As a** portfolio manager,
**I want** a dashboard that shows all files I have submitted and their current status,
**So that** I can monitor progress without needing to contact the operations team.

**Acceptance Criteria:**

- AC-01: The tracker page shows a sortable, filterable table of `FileRecord` entries: `fileId`, `originalFileName`, `flowType`, `status`, `priority`, `ingressChannel`, `createdAt`, `updatedAt`.
- AC-02: Status values are displayed as colour-coded badges (green: COMPLETED, amber: in-progress states, red: failure states).
- AC-03: The table supports filtering by `status`, `flowType`, `ingressChannel`, and date range.
- AC-04: The table auto-refreshes every 10 seconds (configurable).
- AC-05: Clicking a row opens the File Detail view (US-902).

### US-902: File Detail Drill-Down

**As a** portfolio manager,
**I want** to see the complete lifecycle of a specific file — scan result, validation errors, and record-level processing progress,
**So that** I can understand exactly what happened to a submitted file.

**Acceptance Criteria:**

- AC-01: The detail view shows the full status timeline (from `file_status_audit`), including timestamps and actors for each transition.
- AC-02: If the file status is `VALIDATION_FAILED`, the validation errors table is displayed with row number, column name, and error reason.
- AC-03: If the file status is `PROCESSING`, `COMPLETED`, or `PARTIALLY_COMPLETED`, a progress bar and record results table are shown: total records, synced count, failed count, in-progress count.
- AC-04: Failed records are displayed with their row number, error code, and last error message.
- AC-05: Scan result detail is displayed: ClamAV engine version, signature date, scan duration, virus name (if INFECTED).

### US-903: Admin — DLQ Management and Manual Retry

**As a** platform operator,
**I want** a DLQ management screen where I can see all files in terminal failure states and retry them,
**So that** I can recover from transient failures without direct database access.

**Acceptance Criteria:**

- AC-01: The Admin DLQ screen lists all files with status `FAILED`, `QUARANTINED`, `SCAN_ERROR`, or `VALIDATION_FAILED`.
- AC-02: Each row shows the last error message, failure timestamp, number of previous retry attempts, and the responsible stage.
- AC-03: Clicking **Retry** calls `POST /api/v1/admin/dlq/{fileId}/retry` and refreshes the row status.
- AC-04: **Quarantined** files have a **Review** action that opens the scan result detail and an **Approve for Deletion** button — this does not retry the file.
- AC-05: Admin actions require a confirmation dialog before execution.

### US-904: Real-Time Notifications

**As a** platform operator,
**I want** browser notifications for P0 file failures and DLQ growth,
**So that** I am alerted immediately without having to watch the dashboard continuously.

**Acceptance Criteria:**

- AC-01: If a P0 file transitions to a failure state, a browser notification is triggered (requires notification permission).
- AC-02: If DLQ depth increases, a persistent banner appears on the Admin screen.
- AC-03: Notification polling uses the existing 10-second refresh cycle — no WebSocket required for the MVP.

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-901 | The React app is a single-page application (SPA) built with Vite + React 18 + TypeScript. |
| FR-902 | All API calls go through a typed service layer (`api/fileService.ts`) — no direct `fetch()` calls in components. |
| FR-903 | The API base URL must be configurable via environment variable `VITE_API_BASE_URL` — no hardcoded URLs. |
| FR-904 | Error handling: API errors display a user-friendly message in a toast notification; the raw error is logged to the browser console. |
| FR-905 | The app must be usable without a mouse — full keyboard navigation. |

---

## New API Endpoints Required from Backend

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/uploads` | List file records (paginated, filterable) |
| `GET` | `/api/v1/uploads/{fileId}/status` | Get current file status |
| `GET` | `/api/v1/uploads/{fileId}/history` | Get full audit trail |
| `GET` | `/api/v1/uploads/{fileId}/validation-errors` | Get validation error report |
| `GET` | `/api/v1/uploads/{fileId}/records` | Get record results (paginated) |
| `GET` | `/api/v1/admin/dlq` | List DLQ files |
| `POST` | `/api/v1/admin/dlq/{fileId}/retry` | Manual retry |

---

## Non-Functional Requirements

| ID | Requirement | Target |
|---|---|---|
| NFR-901 | Initial page load time | < 2 seconds (p95) |
| NFR-902 | Table refresh latency | < 500 ms for up to 1,000 rows |
| NFR-903 | Browser support | Chrome 120+, Firefox 120+, Safari 17+ |
