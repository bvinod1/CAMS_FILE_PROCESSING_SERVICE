# Phase 0: Research — Epic 7: Configuration Management

**Purpose**: Resolve unknowns identified during planning before starting design.
**Created**: 2026-03-15
**Plan**: [plan.md](plan.md)

## Research Tasks

### 1. Caffeine Cache TTL — Spring `@Cacheable` vs Manual Cache

**Question**: Is Spring's `@Cacheable` with a Caffeine backend the right choice, or should we manage cache entries manually for more control over eviction?

**Findings**:
- Spring's `CacheManager` with `CaffeineCacheManager` auto-configures all declared caches via `spring.cache.caffeine.spec`.
- `@Cacheable("processingConfigCache")` on a service method handles the read-through pattern with zero boilerplate.
- `@CacheEvict("processingConfigCache")` on update methods invalidates immediately on call — no lag.
- Cache stats (hit rate, miss rate, eviction count) are auto-exported via Micrometer (`cache.gets`, `cache.puts`).
- **Decision**: Use `@Cacheable` / `@CacheEvict` on `CachingConfigurationService` with separate `CaffeineSpec` per cache name (`processingConfigCache=expireAfterWrite=60s`, `featureFlagsCache=expireAfterWrite=30s`, `priorityRulesCache=expireAfterWrite=30s`).
- **Rationale**: Minimal code; free observability; TTL tuning via `application.yml` without code change.
- **Alternative rejected**: Manual `LoadingCache<String, ProcessingConfig>` — same capability, more boilerplate, no Spring integration.

### 2. JSONB Column for `retry_backoff_ms` Array

**Question**: How should the exponential backoff interval array (e.g. `[1000, 2000, 4000]`) be stored in PostgreSQL?

**Findings**:
- A `JSONB` column (`retry_backoff_ms JSONB NOT NULL DEFAULT '[1000, 2000, 4000]'`) stores the array natively.
- JPA reads it as a `String` and the service deserializes with Jackson (`ObjectMapper.readValue`).
- Alternatively, store as a comma-separated `VARCHAR` — simpler but harder to validate and query.
- Spring Data JPA `@Convert` annotation with a `List<Long>` converter handles transparent serialisation/deserialisation.
- **Decision**: `JSONB` column with a `@Convert(converter = LongListJsonConverter.class)` JPA attribute converter.
- **Rationale**: Type-safe; standard; extensible if more complex retry config is needed in future.
- **Alternative rejected**: `VARCHAR` comma-separated — parsing ambiguity; no schema validation.

### 3. Feature Flag Enforcement — AOP vs Explicit Check

**Question**: Should feature flag enforcement use Spring AOP (`@Around` advice on worker methods), or explicit checks at the top of each worker?

**Findings**:
- AOP interception on `@RabbitListener` methods is unreliable — Spring AMQP creates proxies that can bypass `@Around` advice depending on configuration.
- Explicit `if (!featureFlags.scanningEnabled()) { ... }` at the top of each worker is simple, debuggable, and impossible to bypass.
- Feature flag check is a single `ConfigurationPort` call — with caching, it costs < 1 ms.
- **Decision**: Explicit checks at the top of each worker (`ScanService`, `ValidationWorker`, `ProcessingWorker`).
- **Rationale**: Reliability over elegance. An AOP proxy failure that silently bypasses a feature flag in a financial platform is unacceptable.
- **Alternative rejected**: AOP advice — proxy instability with AMQP listeners; harder to unit test the bypass logic.

### 4. Config Versioning — Optimistic Locking vs Append-Only

**Question**: Should `config_templates` use JPA `@Version` optimistic locking or a simple version integer with old rows retained?

**Findings**:
- `@Version` prevents concurrent updates but does not retain history — the old value is overwritten.
- Retaining old rows (with `active = FALSE`) gives an auditable history without a separate history table.
- The `validation_results` table records `template_version` — being able to look up the exact rules used for a historical scan is a compliance requirement.
- **Decision**: Integer `version` column incremented on every UPDATE. Old row retained with `active = FALSE`. New active row created with incremented version.
- **Rationale**: Compliance requirement for historical template lookup. Simple pattern; no extra table needed.
- **Alternative rejected**: Separate `template_history` table — unnecessary; the `active` flag on the main table is simpler.

### 5. Admin API Security — RBAC vs Network Isolation

**Question**: Should the admin config API be secured with RBAC (Spring Security roles) or simply isolated to an internal network port?

**Findings**:
- For the MVP, network isolation (internal GKE service, not exposed via Ingress) is the simplest approach.
- Spring Security `@PreAuthorize("hasRole('ROLE_ADMIN')")` provides RBAC that is easy to add without changing API contracts.
- The openapi.yaml already defines `bearerAuth` security scheme for the admin endpoints.
- **Decision**: Add `@PreAuthorize("hasRole('ROLE_ADMIN')")` to all `ConfigAdminController` methods. In the local profile, configure a default admin user in `application-local.yml`.
- **Rationale**: Explicit RBAC is more robust than network-only isolation; it works in all deployment topologies and is required for audit logging (actor = authenticated user).
- **Alternative rejected**: Network isolation only — insufficient for an admin API used by humans; no actor identity for audit logs.
