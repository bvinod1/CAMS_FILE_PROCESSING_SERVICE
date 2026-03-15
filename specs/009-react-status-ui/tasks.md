# Tasks — Epic 9: React Status Dashboard

## T901 — Project Scaffolding (Vite + React 18 + TypeScript)

**Task:** Scaffold the React frontend application.

**Subtasks:**
- T901-A: Run `npm create vite@latest frontend -- --template react-ts` from the workspace root
- T901-B: Add dependencies: `axios` (HTTP client), `react-router-dom` v6 (routing), `@tanstack/react-query` v5 (data fetching + caching), `lucide-react` (icons)
- T901-C: Add dev dependencies: `@types/react`, `@types/react-dom`, `vitest`, `@testing-library/react`
- T901-D: Create `.env` and `.env.local` files with `VITE_API_BASE_URL=http://localhost:8080`, `VITE_POLL_INTERVAL_MS=10000`
- T901-E: Verify `npm run dev` serves the app at `http://localhost:5173`

**Acceptance:** Dev server starts; default Vite page loads in browser.

---

## T902 — TypeScript Type Definitions

**Task:** Create TypeScript types matching all backend API DTOs.

**Subtasks:**
- T902-A: Create `frontend/src/api/types.ts` with interfaces: `FileRecord`, `FileStatusUpdate`, `AuditEntry`, `ValidationError`, `RecordResult`, `RecordResultsPage`, `FileRecordsPage`, `DlqEntry`, `RetryResponse`
- T902-B: Create `FileStatus` and `RecordStatus` TypeScript enums matching backend values
- T902-C: Ensure all date fields are typed as `string` (ISO 8601) — no `Date` objects in API layer

**Acceptance:** TypeScript compiles with zero errors (`npm run build`).

---

## T903 — API Service Layer

**Task:** Create the typed API service layer — no `fetch()` calls in components.

**Subtasks:**
- T903-A: Create `frontend/src/api/fileService.ts` with functions:
  - `listFiles(filters, page, size) → Promise<FileRecordsPage>`
  - `getFileStatus(fileId) → Promise<FileStatusUpdate>`
  - `getFileHistory(fileId) → Promise<AuditEntry[]>`
  - `getValidationErrors(fileId) → Promise<ValidationError[]>`
  - `getRecordResults(fileId, status, page, size) → Promise<RecordResultsPage>`
- T903-B: Create `frontend/src/api/adminService.ts` with:
  - `getDlqEntries() → Promise<DlqEntry[]>`
  - `retryFile(fileId) → Promise<RetryResponse>`
- T903-C: Configure Axios base URL from `import.meta.env.VITE_API_BASE_URL`
- T903-D: Add global Axios response interceptor: log errors to console, throw typed `ApiError`

**Acceptance:** `npm run build` succeeds; service functions have correct TypeScript signatures.

---

## T904 — File Tracker Page (US-901)

**Task:** Build the main file list table page.

**Subtasks:**
- T904-A: Create `frontend/src/components/FileTable/FileStatusBadge.tsx` — renders status with correct colour from `data-model.md` badge colour table
- T904-B: Create `frontend/src/components/FileTable/FileTableFilters.tsx` — dropdowns for status, flowType, ingressChannel; date range inputs
- T904-C: Create `frontend/src/components/FileTable/FileTable.tsx` — sortable table using `@tanstack/react-query` with pagination controls
- T904-D: Create `frontend/src/pages/FilesPage.tsx` composing the above components; clicking a row navigates to `/files/{fileId}`
- T904-E: Auto-refresh: use `useAutoRefresh` hook (T906) with `refetchInterval` from `VITE_POLL_INTERVAL_MS`

**Acceptance:** Table renders; status filters work; row click navigates; auto-refresh visible in network tab.

---

## T905 — File Detail Page (US-902)

**Task:** Build the file detail drill-down page.

**Subtasks:**
- T905-A: Create `frontend/src/components/FileDetail/StatusTimeline.tsx` — ordered list of audit transitions with timestamps and actors
- T905-B: Create `frontend/src/components/FileDetail/ScanResultCard.tsx` — shows ClamAV engine version, signature date, scan duration, virus name (if INFECTED)
- T905-C: Create `frontend/src/components/FileDetail/ValidationErrorsTable.tsx` — shown only when status = `VALIDATION_FAILED`
- T905-D: Create `frontend/src/components/FileDetail/RecordResultsTable.tsx` — paginated; progress bar for PROCESSING; filter by status; failed records show error detail
- T905-E: Create `frontend/src/pages/FileDetailPage.tsx` composing all panels; back navigation to `/files`

**Acceptance:** Detail page renders all sections; conditional sections (validation errors, records) appear/hide based on status.

---

## T906 — useAutoRefresh Hook

**Task:** Create a React hook that polls at a configurable interval and pauses when the browser tab is not visible.

**Subtasks:**
- T906-A: Create `frontend/src/hooks/useAutoRefresh.ts`
- T906-B: Use `document.addEventListener("visibilitychange", ...)` to pause polling when tab is hidden
- T906-C: Accept `intervalMs` parameter (default: `VITE_POLL_INTERVAL_MS`)
- T906-D: Return `{ isRefreshing, lastRefreshedAt, refresh }` for UI use
- T906-E: Write Vitest unit test: mock `document.visibilityState` → assert callback not called when hidden

**Acceptance:** Unit test passes; polling stops when tab hidden.

---

## T907 — Admin DLQ Page (US-903)

**Task:** Build the admin DLQ management screen.

**Subtasks:**
- T907-A: Create `frontend/src/components/Admin/DLQTable.tsx` — table of failed files with last error, timestamp, retry count, and action buttons
- T907-B: Create `frontend/src/components/Admin/RetryButton.tsx` — shows confirmation `ConfirmDialog` before calling `adminService.retryFile()`; disables itself after successful retry
- T907-C: Create `frontend/src/components/common/ConfirmDialog.tsx` — accessible modal with Cancel/Confirm buttons
- T907-D: Create `frontend/src/pages/AdminPage.tsx` with the DLQ table and DLQ depth banner
- T907-E: Add routes: `/files` → FilesPage, `/files/:fileId` → FileDetailPage, `/admin` → AdminPage

**Acceptance:** Retry button calls API; confirmation dialog blocks accidental clicks; route navigation works.

---

## T908 — Error Handling and Toast Notifications

**Task:** Implement global error handling with user-friendly toast notifications.

**Subtasks:**
- T908-A: Create `frontend/src/components/common/ErrorToast.tsx` — dismissible toast notification component
- T908-B: Wire the Axios error interceptor (T903-D) to dispatch toast events via a custom event or state management
- T908-C: Display friendly messages: 404 → "File not found", 409 → "Cannot retry — operation in progress", 500 → "Server error, please try again"
- T908-D: Raw error details logged to `console.error()` alongside the friendly message

**Acceptance:** API error in browser triggers toast; error details visible in console.

---

## T909 — Keyboard Navigation and Accessibility

**Task:** Verify full keyboard accessibility across all interactive elements.

**Subtasks:**
- T909-A: Verify all buttons, links, and interactive table rows are reachable via Tab key
- T909-B: `ConfirmDialog` traps focus within the modal while open; `Escape` key closes it
- T909-C: `FileStatusBadge` uses `aria-label` to announce status colour meaning
- T909-D: Table rows include `role="button"` and `onKeyDown` Enter handler for keyboard-driven navigation
- T909-E: Run `@axe-core/react` accessibility audit in development mode; fix all `critical` and `serious` violations

**Acceptance:** Zero `critical` or `serious` axe violations; full tab navigation confirmed manually.

---

## T910 — Build Configuration and Docker Integration

**Task:** Add the React app to the project's Docker Compose stack for local development.

**Subtasks:**
- T910-A: Create `frontend/Dockerfile` using `node:20-alpine` multi-stage build: `npm run build` → serve with `nginx:alpine`
- T910-B: Add `frontend` service to `docker-compose.yml`: port `3001:80`, depends_on CAMS app
- T910-C: Configure nginx to proxy `/api/v1/*` to the Spring Boot container (avoid CORS issues in Docker)
- T910-D: Add `npm run build` step to CI workflow (create `.github/workflows/ci.yml` if not present)
- T910-E: Verify `docker-compose up` → React app accessible at `http://localhost:3001`

**Acceptance:** `docker-compose up` starts the React app; Files page loads and shows the API data.
