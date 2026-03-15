# Phase 0: Research — Epic 3: Header Validation Pipeline

**Purpose**: Resolve unknowns identified during planning before starting design.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## Research Tasks

### 1. Streaming the First Line of a File Without Full Load

**Question**: How do we read only the first line of a large file (potentially several GB) from MinIO or GCS without loading the whole file into memory?

**Findings**:
- Both the AWS S3 SDK v2 (used for MinIO) and the GCS SDK support range reads via HTTP `Range: bytes=0-N`.
- For CSV files the header row is never known in advance, but reading the first 8 KB is always sufficient for any realistic header row.
- `ObjectStoragePort.retrieve(objectName)` returns an `InputStream`. Wrap it in a `BufferedReader` and call `readLine()` exactly once, then close immediately.
- **Decision**: Call `ObjectStoragePort.retrieve()` + `BufferedReader.readLine()` — close the stream after the first line. The S3/GCS SDK will abort the connection, fetching at most one TCP segment.
- **Rationale**: No temp files, no byte arrays, no full load. Guaranteed O(1) memory regardless of file size.
- **Alternative rejected**: Downloading to a temp file then reading line 1 — unnecessary disk I/O.

### 2. Caffeine Cache vs Spring `@Cacheable`

**Question**: Should template caching use Spring's `@Cacheable` annotation with a Caffeine backend, or a hand-rolled cache?

**Findings**:
- Spring Boot auto-configures Caffeine when `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine` are on the classpath.
- `@Cacheable("validationTemplateCache")` with a `CaffeineSpec` provides TTL, maximum size, and stats in one annotation.
- Cache stats are automatically exposed via Micrometer (`cache.gets`, `cache.puts`, `cache.evictions`) — free observability.
- **Decision**: Use `@Cacheable` on `CachingValidationTemplateService` with a `CaffeineSpec` of `expireAfterWrite=60s,maximumSize=500`.
- **Rationale**: Zero boilerplate, integrates with existing Micrometer setup, easy TTL tuning via config.
- **Alternative rejected**: Manual `ConcurrentHashMap` with a scheduled eviction thread — more code, no stats.

### 3. JSON Column for Column Rules in PostgreSQL

**Question**: Is storing column rules as a JSONB column in PostgreSQL better than a separate `template_columns` table?

**Findings**:
- JSONB allows the entire rules array to be read in a single row fetch — no JOIN required.
- Updates to the rules array (adding/removing columns) are single-row UPDATEs rather than INSERT/DELETE on a child table.
- PostgreSQL JSONB supports GIN indexing for path queries, though we only ever query by `flowType` and never by column-rule content.
- Schema changes to the rule format (e.g. adding a new field) require no DDL migration — only the application's deserialisation code changes.
- **Decision**: Store `column_rules` as a `JSONB` column in `validation_templates`.
- **Rationale**: Simpler schema, single-row reads, and schema-evolution flexibility for a financial platform that will add new flow types regularly.
- **Alternative rejected**: Separate `template_columns` rows — requires a JOIN on every template read and complex UPDATE logic when reordering columns.

### 4. Error Code Design — Single Pass vs Fail-Fast

**Question**: Should validation stop at the first error, or collect all column errors before returning?

**Findings**:
- Financial file onboarding teams need to know all problems in one submission — fail-fast forces multiple submit/reject cycles.
- Iterating all expected columns once is O(n) in the number of template columns (typically 5–50). No performance concern.
- Industry reference: OpenAPI validation libraries (e.g. `swagger-request-validator`) use collect-all patterns.
- **Decision**: Collect all errors in a single pass; return them all in `ValidationCompletedEvent` and persist to `validation_errors`.
- **Rationale**: Better operational UX; no performance downside.
- **Alternative rejected**: Fail-fast on first error — rejected by data operations team in requirements review.

### 5. Template Versioning Strategy

**Question**: How should template versions be tracked — event sourcing, optimistic locking, or simple integer increment?

**Findings**:
- Event sourcing is overkill for a configuration table with at most ~50 rows and low write frequency.
- PostgreSQL optimistic locking (`@Version` in JPA) prevents concurrent updates but does not give a human-readable history.
- Simple integer `version` column incremented on every UPDATE, with the old row copied to a `template_version_history` table, provides an auditable trail without event sourcing complexity.
- **Decision**: `version INTEGER` auto-incremented on update; old versions retained in `validation_templates` with `active = FALSE`.
- **Rationale**: Simple, readable history; no additional infrastructure. The `validation_results` table records which `template_version` was used for each file.
- **Alternative rejected**: Full event sourcing — disproportionate complexity for a config table.
