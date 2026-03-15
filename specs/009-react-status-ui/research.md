# Phase 0: Research — Epic 9: React Status Dashboard

**Purpose**: Resolve unknowns identified during planning before starting design.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## Research Tasks

### 1. TanStack Query v5 vs SWR for Auto-Refreshing Tables

**Question**: Should data fetching use TanStack Query (React Query) v5 or SWR, and is `refetchInterval` sufficient for the 10-second auto-refresh requirement?

**Findings**:
- TanStack Query v5 has a significantly richer API: `refetchInterval`, `refetchIntervalInBackground`, window focus refetch, and stale-while-revalidate semantics.
- SWR (`swr` package) is simpler but has less flexibility for complex caching scenarios (e.g. updating a single record in a large paginated list).
- `useQuery({ queryKey: ['files', filters], queryFn: fetchFiles, refetchInterval: pollIntervalMs })` handles auto-refresh in one line.
- `refetchIntervalInBackground: false` automatically pauses polling when the browser tab is not focused — matching the spec requirement, no manual `visibilitychange` listener needed.
- **Decision**: TanStack Query v5 with `refetchInterval` and `refetchIntervalInBackground: false`.
- **Rationale**: Built-in tab visibility handling eliminates T906 manual implementation. Richer caching API covers pagination and detail views.
- **Alternative rejected**: SWR — no built-in tab visibility pause without a custom `isDocumentVisible()` implementation.

### 2. Axios vs Fetch API for the Service Layer

**Question**: Should `fileService.ts` use Axios or the native `fetch` API?

**Findings**:
- Axios provides automatic JSON serialisation/deserialisation, configurable base URL (`axios.create({ baseURL })`), and request/response interceptors for global error handling.
- The native `fetch` API requires manual `response.json()` calls and interceptor-equivalent code.
- Axios response interceptors make it trivial to implement the global error-to-toast pattern: one interceptor for all `4xx`/`5xx` errors.
- Bundle size: Axios adds ~14 KB gzipped — acceptable for a dashboard app.
- **Decision**: Axios with a base instance configured from `import.meta.env.VITE_API_BASE_URL`, plus a response interceptor for error handling.
- **Rationale**: Simpler service layer code; clean interceptor pattern for toast notifications.
- **Alternative rejected**: Native fetch — more boilerplate per request; harder to add global error handling.

### 3. nginx Reverse Proxy — CORS vs Upstream Proxy

**Question**: Should the React app call the Spring Boot API directly (with CORS configured) or through an nginx reverse proxy?

**Findings**:
- Direct calls from React on `localhost:3001` to Spring Boot on `localhost:8080` require CORS configuration on the backend (`@CrossOrigin` or `WebFluxConfigurer.addCorsMappings`).
- nginx proxy (`location /api/ { proxy_pass http://cams-app:8080/api/; }`) eliminates CORS entirely — both the app and API are served from the same origin (`localhost:3001`).
- nginx also handles SPA routing: `try_files $uri $uri/ /index.html` ensures React Router deep links work on page refresh.
- **Decision**: nginx reverse proxy in Docker Compose. `VITE_API_BASE_URL` is empty string in Docker (same origin); set to `http://localhost:8080` in local `vite dev` mode.
- **Rationale**: No CORS configuration on the backend; SPA routing handled correctly; production-realistic topology.
- **Alternative rejected**: CORS on backend — adds backend configuration complexity; different behaviour in dev vs Docker.

### 4. Status Badge Colour Accessibility

**Question**: Are the specified status badge colours (from `data-model.md`) WCAG AA compliant for text contrast?

**Findings**:
- Green `#16a34a` text on white background: contrast ratio 4.6:1 — **WCAG AA Pass** (minimum 4.5:1 for normal text).
- Red `#dc2626` text on white background: contrast ratio 5.3:1 — **WCAG AA Pass**.
- Amber `#d97706` text on white background: contrast ratio 2.7:1 — **WCAG AA FAIL**.
- Blue `#2563eb` text on white background: contrast ratio 5.0:1 — WCAG AA Pass.
- **Decision**: Change amber badge to use `#92400e` (dark amber) on `#fef3c7` background — contrast 7.0:1, WCAG AA Pass. Update `data-model.md` colour table.
- **Rationale**: Accessibility is a spec requirement (T909). Changing the colour at design time is cheaper than fixing it in code review.
- **Alternative rejected**: Keeping `#d97706` — fails WCAG AA for normal text; will be flagged by axe-core audit.

### 5. Pagination Strategy — Cursor vs Offset/Page

**Question**: Should the file list API use cursor-based pagination or offset/page pagination?

**Findings**:
- Offset/page pagination (`page=0&size=20`) is simpler to implement in Spring Data JPA (`Pageable`) and easier to display in a UI (page number navigation).
- Cursor-based pagination is more efficient for large datasets but requires a stable sort key and is harder to implement page-number navigation for.
- The file tracker table is expected to show at most a few thousand rows at any given time (active files) — offset/page performance is fine.
- **Decision**: Offset/page pagination (`page`, `size` query parameters) consistent with Spring Data `Pageable`.
- **Rationale**: Simpler implementation on both backend and frontend; sufficient for expected data volumes.
- **Alternative rejected**: Cursor-based — unnecessary for this data volume; harder to implement "page 5 of 12" UI.
