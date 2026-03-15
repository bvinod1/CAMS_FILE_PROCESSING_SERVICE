# Data Model — Epic 9: React Status Dashboard

## Backend API Contracts (New Endpoints Required)

All endpoints follow the same conventions as `openapi.yaml` in Epic 1: JSON, versioned at `/api/v1`, bearer token auth.

### `GET /api/v1/uploads` — List File Records

**Query Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `status` | `string` | Filter by FileStatus value |
| `flowType` | `string` | Filter by flow type |
| `ingressChannel` | `string` | `REST`, `SFTP`, `GCS_TRIGGER` |
| `priority` | `string` | `P0`, `P1`, `P2` |
| `from` | `ISO8601` | Created-at range start |
| `to` | `ISO8601` | Created-at range end |
| `page` | `integer` | Page number (0-indexed) |
| `size` | `integer` | Page size (default 20, max 100) |

**Response:**
```json
{
  "content": [
    {
      "fileId":           "uuid",
      "originalFileName": "nav_20240115.csv",
      "flowType":         "NAV",
      "status":           "COMPLETED",
      "priority":         "P0",
      "ingressChannel":   "REST",
      "fileSizeBytes":    204800,
      "createdAt":        "2024-01-15T10:00:00Z",
      "updatedAt":        "2024-01-15T10:05:00Z"
    }
  ],
  "totalElements": 142,
  "totalPages":    8,
  "page":          0,
  "size":          20
}
```

---

### `GET /api/v1/uploads/{fileId}/status`

**Response:**
```json
{
  "fileId":    "uuid",
  "status":    "PROCESSING",
  "updatedAt": "2024-01-15T10:04:00Z"
}
```

---

### `GET /api/v1/uploads/{fileId}/history`

**Response:**
```json
{
  "fileId": "uuid",
  "history": [
    {
      "fromStatus":      "AWAITING_UPLOAD",
      "toStatus":        "UPLOADED",
      "actor":           "user@example.com",
      "reason":          null,
      "transitionedAt":  "2024-01-15T10:01:00Z"
    }
  ]
}
```

---

### `GET /api/v1/uploads/{fileId}/validation-errors`

**Response:**
```json
{
  "fileId":           "uuid",
  "templateVersion":  3,
  "result":           "FAIL",
  "errorCount":       2,
  "errors": [
    {
      "rowNumber":      0,
      "columnName":     "NAV_DATE",
      "columnPosition": 1,
      "errorCode":      "WRONG_POSITION",
      "errorMessage":   "Expected position 1 but found at position 2"
    }
  ]
}
```

---

### `GET /api/v1/uploads/{fileId}/records`

**Query Parameters:** `status` (filter), `page`, `size`

**Response:**
```json
{
  "content": [
    {
      "rowNumber":      1,
      "status":         "SYNCED",
      "retryCount":     1,
      "responseCode":   "200",
      "errorCode":      null,
      "errorMessage":   null,
      "processedAt":    "2024-01-15T10:04:30Z",
      "durationMs":     143
    }
  ],
  "totalElements": 10000,
  "syncedCount":   9995,
  "failedCount":   5,
  "skippedCount":  0
}
```

---

### `GET /api/v1/admin/dlq`

**Response:**
```json
{
  "entries": [
    {
      "fileId":           "uuid",
      "originalFileName": "nav_20240114.csv",
      "flowType":         "NAV",
      "priority":         "P0",
      "failedAtStatus":   "SCAN_ERROR",
      "lastErrorMessage": "ClamAV timeout",
      "failedAt":         "2024-01-14T08:00:00Z",
      "retryEligible":    true
    }
  ]
}
```

---

### `POST /api/v1/admin/dlq/{fileId}/retry`

**Request body:** (empty)

**Response:**
```json
{
  "fileId":          "uuid",
  "previousStatus":  "SCAN_ERROR",
  "resetToStatus":   "UPLOADED",
  "retryScheduledAt": "2024-01-15T10:30:00Z"
}
```

---

## React App Structure

```
frontend/
  src/
    api/
      fileService.ts          # All API calls (typed)
      adminService.ts         # Admin DLQ API calls
      types.ts                # TypeScript types matching backend DTOs
    components/
      FileTable/
        FileTable.tsx
        FileStatusBadge.tsx
        FileTableFilters.tsx
      FileDetail/
        FileDetail.tsx
        StatusTimeline.tsx
        ValidationErrorsTable.tsx
        RecordResultsTable.tsx
        ScanResultCard.tsx
      Admin/
        DLQTable.tsx
        RetryButton.tsx
      common/
        LoadingSpinner.tsx
        ErrorToast.tsx
        ConfirmDialog.tsx
        Pagination.tsx
    pages/
      FilesPage.tsx
      FileDetailPage.tsx
      AdminPage.tsx
    hooks/
      useAutoRefresh.ts       # 10s polling hook with visibility API pause
      useFileRecords.ts
      useDLQ.ts
    App.tsx
    main.tsx
  public/
  index.html
  vite.config.ts
  tsconfig.json
```

---

## Status Badge Colour Mapping

| Status | Colour | Hex |
|---|---|---|
| `COMPLETED` | Green | `#16a34a` |
| `SCANNED_CLEAN`, `VALIDATED` | Teal | `#0d9488` |
| `PENDING_UPLOAD`, `AWAITING_UPLOAD`, `UPLOADED` | Grey | `#6b7280` |
| `SCANNING`, `VALIDATING`, `PROCESSING` | Blue (animated pulse) | `#2563eb` |
| `PARTIALLY_COMPLETED` | Amber | `#d97706` |
| `QUARANTINED`, `SCAN_ERROR`, `VALIDATION_FAILED`, `FAILED` | Red | `#dc2626` |

---

## Environment Variables (.env)

```
VITE_API_BASE_URL=http://localhost:8080
VITE_POLL_INTERVAL_MS=10000
VITE_PAGE_SIZE_DEFAULT=20
```
