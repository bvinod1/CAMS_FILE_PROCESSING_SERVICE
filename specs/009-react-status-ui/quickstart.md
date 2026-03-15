# Quickstart: Epic 9 — React Status Dashboard

**Purpose**: Guide for running and using the React status dashboard locally.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## 1. Prerequisites

- Java 21 (`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`)
- Maven 3.9+
- Node.js 20+ (`node --version`)
- Docker Desktop (running)
- Epics 1–5 implemented — the Dashboard requires the status API endpoints

## 2. Option A: Run via Docker Compose (Recommended)

```bash
docker-compose up -d
```

The React app builds and starts automatically. Open in browser:

```
http://localhost:3001
```

The nginx reverse proxy routes `/api/v1/*` to the Spring Boot backend on port 8080.

## 3. Option B: Run in Development Mode (Hot Reload)

For active frontend development with hot module replacement:

### Terminal 1 — Start the backend and infrastructure
```bash
docker-compose up -d postgres rabbitmq minio clamav
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Terminal 2 — Start the Vite dev server
```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` — the Vite dev server proxies API calls to `http://localhost:8080`.

## 4. Files Page — File Tracker

1. Open `http://localhost:3001` (or `http://localhost:5173` in dev mode)
2. The **Files** page shows all `FileRecord` entries in a sortable table
3. Use the filters to narrow by **Status**, **Flow Type**, **Ingress Channel**, or date range
4. The table **auto-refreshes every 10 seconds** — watch the status badges update as files progress through the pipeline

### Upload a file and watch it appear in the table

```bash
curl -s -X POST http://localhost:8080/api/v1/uploads \
  -H "Content-Type: application/json" \
  -d '{ "fileName": "nav-20240115.csv", "flowType": "NAV", "fileSizeBytes": 2048, "checksumMd5": "d41d8cd98f00b204e9800998ecf8427e" }' | jq .
```

Confirm the file, then watch its status badge change in the UI: grey → blue (animated) → green.

## 5. File Detail Page

Click any row in the Files table to open the **File Detail** page. Sections visible depend on the file's status:

| Status | Sections Shown |
|---|---|
| Any | Status Timeline (full audit trail) |
| `SCANNED_CLEAN` or later | Scan Result Card (ClamAV engine, signature date, duration) |
| `VALIDATION_FAILED` | Validation Errors Table |
| `PROCESSING` / `COMPLETED` / `PARTIALLY_COMPLETED` | Record Results Table with progress bar |

## 6. Admin DLQ Page

1. Navigate to `http://localhost:3001/admin`
2. Any files in `FAILED`, `QUARANTINED`, `SCAN_ERROR`, or `VALIDATION_FAILED` appear in the DLQ table
3. Click **Retry** → confirm the dialog → file is reset and re-queued

To generate a DLQ entry for testing:
```bash
# Stop ClamAV to trigger SCAN_ERROR
docker-compose stop clamav
# Upload and confirm a file — it will time out and enter SCAN_ERROR
# Restart ClamAV
docker-compose start clamav
# Open Admin page — file appears in DLQ, Retry button available
```

## 7. Run Frontend Tests

```bash
cd frontend

# Unit and component tests (Vitest)
npm run test

# Accessibility audit (axe-core — runs in browser automatically in dev mode)
npm run dev
# Open http://localhost:5173 and check browser console for axe violations
```

## 8. Build the Production Bundle

```bash
cd frontend
npm run build
# Output in frontend/dist/
# Bundle size reported — verify main chunk < 500 KB gzipped
```

## 9. Environment Variables Reference

| Variable | Default (dev) | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Backend API base URL |
| `VITE_POLL_INTERVAL_MS` | `10000` | Auto-refresh interval (ms) |
| `VITE_PAGE_SIZE_DEFAULT` | `20` | Default table page size |

Set in `frontend/.env.local` to override defaults without modifying `.env`.
