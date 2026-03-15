# Implementation Plan: Epic 9 — React Status Dashboard

**Branch**: `009-react-status-ui` | **Date**: 2026-03-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/009-react-status-ui/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

This plan delivers a React 18 + TypeScript single-page application (SPA) that provides real-time visibility into the CAMS file processing pipeline. Portfolio managers see a filterable, auto-refreshing file tracker table with status badges. A detail drill-down shows the full audit trail, scan result, validation errors, and per-record processing progress. Platform operators have an admin DLQ screen with manual retry capability. The frontend is built with Vite, communicates exclusively through a typed API service layer, and is served via an nginx container in Docker Compose with a reverse proxy to the Spring Boot backend.

## Technical Context

**Language/Version**: TypeScript 5.x / React 18  
**Primary Dependencies**: Vite 5.x, React 18, React Router v6, TanStack Query v5 (data fetching + caching), Axios (HTTP client), Lucide React (icons)  
**Storage**: N/A — pure frontend consuming the backend REST API  
**Testing**: Vitest, @testing-library/react, @axe-core/react (accessibility audit)  
**Target Platform**: Chrome 120+, Firefox 120+, Safari 17+; served via nginx in Docker  
**Project Type**: Web application (frontend SPA)  
**Performance Goals**: Initial page load < 2 seconds (p95); table refresh < 500 ms for 1,000 rows  
**Constraints**: No hardcoded API URLs (`VITE_API_BASE_URL` env var); no direct `fetch()` in components; full keyboard navigation  
**Scale/Scope**: 7 new backend API endpoints consumed; 3 pages (Files, Detail, Admin); 10-second auto-refresh polling

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **[PASS] API-First**: All 7 new backend endpoints are defined in `openapi.yaml` before the React code is written.
- **[PASS] No Hardcoded Config**: All API URLs, poll intervals, and page sizes come from `VITE_*` environment variables.
- **[PASS] Typed API Layer**: All HTTP calls go through `fileService.ts` / `adminService.ts`. Components never call `axios` or `fetch` directly.
- **[PASS] Accessibility**: `@axe-core/react` audit runs in development mode; all critical/serious violations fixed before merge.
- **[PASS] Error Handling**: API errors display friendly toast notifications; raw errors logged to console; no unhandled promise rejections.
- **[PASS] Local-First**: React app served at `http://localhost:3001` via `docker-compose up`. No external dependencies needed.

## Project Structure

### Documentation (this feature)

```text
specs/009-react-status-ui/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root — web application structure)

```text
frontend/
├── src/
│   ├── api/
│   │   ├── types.ts          # TypeScript interfaces for all API DTOs
│   │   ├── fileService.ts    # File-related API calls
│   │   └── adminService.ts   # Admin DLQ API calls
│   ├── components/
│   │   ├── FileTable/
│   │   │   ├── FileTable.tsx
│   │   │   ├── FileStatusBadge.tsx
│   │   │   └── FileTableFilters.tsx
│   │   ├── FileDetail/
│   │   │   ├── FileDetail.tsx
│   │   │   ├── StatusTimeline.tsx
│   │   │   ├── ValidationErrorsTable.tsx
│   │   │   ├── RecordResultsTable.tsx
│   │   │   └── ScanResultCard.tsx
│   │   ├── Admin/
│   │   │   ├── DLQTable.tsx
│   │   │   └── RetryButton.tsx
│   │   └── common/
│   │       ├── LoadingSpinner.tsx
│   │       ├── ErrorToast.tsx
│   │       ├── ConfirmDialog.tsx
│   │       └── Pagination.tsx
│   ├── hooks/
│   │   ├── useAutoRefresh.ts
│   │   ├── useFileRecords.ts
│   │   └── useDLQ.ts
│   ├── pages/
│   │   ├── FilesPage.tsx
│   │   ├── FileDetailPage.tsx
│   │   └── AdminPage.tsx
│   ├── App.tsx
│   └── main.tsx
├── .env                  # VITE_API_BASE_URL, VITE_POLL_INTERVAL_MS
├── Dockerfile
├── nginx.conf
├── vite.config.ts
├── tsconfig.json
└── package.json

src/                      # Spring Boot backend (existing)
└── main/java/...         # New endpoints added to UploadController, AdminController
```

**Structure Decision**: Web application structure with `frontend/` at repo root alongside `src/`. Backend additions go into existing controllers. nginx serves the React SPA and proxies `/api/v1/*` to the Spring Boot container.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *N/A*     | —          | —                                   |
