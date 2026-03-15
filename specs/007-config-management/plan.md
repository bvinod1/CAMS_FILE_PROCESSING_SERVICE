# Implementation Plan: Epic 7 — Configuration Management

**Branch**: `007-config-management` | **Date**: 2026-03-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/007-config-management/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

This plan delivers runtime configuration management for the CAMS platform. Business rules — validation template column schemas, Spring Batch chunk sizes, retry policies, feature flags, and priority mappings — are stored in the database and exposed via a versioned admin REST API. All config reads go through `ConfigurationPort`, backed by Caffeine caches (30–60s TTL) for performance. All mutations are audit-logged to `config_audit` (INSERT-only). The system starts with seeded defaults for `NAV` and `TRANSACTION` flow types. Every pipeline worker reads from `ConfigurationPort` — no hardcoded values in `application.yml`.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.2.3, Caffeine Cache, Spring Data JPA (local), Spring Data Spanner (gcp), Spring Boot Actuator  
**Storage**: PostgreSQL (local) / Cloud Spanner (gcp) — `config_templates`, `feature_flags`, `priority_rules`, `config_audit`  
**Testing**: JUnit 5, Testcontainers (PostgreSQL), MockMvc  
**Target Platform**: GKE (gcp profile); Docker Compose (local profile)  
**Project Type**: Admin REST API + in-memory caching layer  
**Performance Goals**: Config cache hit < 5 ms (p99); cache miss < 50 ms (p99); admin API response < 200 ms (p99)  
**Constraints**: Template changes effective within cache TTL (≤ 60 seconds); `config_audit` is INSERT-only; in-flight job not interrupted by config change  
**Scale/Scope**: Config table has at most ~100 rows total; read-heavy (every pipeline operation reads config), write-light (admin changes)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **[PASS] Program to the Interface**: All config reads in business logic go through `ConfigurationPort`. No service has a direct `JpaRepository` import for config.
- **[PASS] Local Adapter Mandatory**: `PostgresConfigurationRepository` (`@Profile("local")`) and `SpannerConfigurationRepository` (`@Profile("gcp")`) both implement `ConfigurationPort`.
- **[PASS] No Mocks in Tests**: Config integration test uses Testcontainers PostgreSQL.
- **[PASS] Audit Trail**: `config_audit` is INSERT-only. All mutations write an audit row in the same transaction.
- **[PASS] Cache Safety**: `@CacheEvict` on all update methods ensures stale config cannot persist beyond the TTL.

## Project Structure

### Documentation (this feature)

```text
specs/007-config-management/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
src/
├── main/
│   └── java/com/cams/fileprocessing/
│       ├── interfaces/
│       │   └── ConfigurationPort.java
│       ├── business/config/
│       │   ├── ProcessingConfig.java     # record
│       │   └── FeatureFlags.java         # record
│       ├── infrastructure/
│       │   ├── local/
│       │   │   └── PostgresConfigurationRepository.java
│       │   └── gcp/
│       │       └── SpannerConfigurationRepository.java
│       └── features/config/
│           ├── CachingConfigurationService.java
│           └── ConfigAdminController.java
└── test/
    └── java/com/cams/fileprocessing/
        ├── features/config/
        │   ├── ConfigAdminControllerTest.java  # MockMvc (8 endpoints)
        │   └── CachingConfigurationServiceTest.java  # cache hit/miss/evict
        └── infrastructure/config/
            └── PostgresConfigurationIntegrationTest.java  # Testcontainers
```

**Structure Decision**: `ConfigurationPort` in `interfaces/`, domain records in `business/config/`, adapters in `infrastructure/`, caching service + controller in `features/config/`. Keeps the cache decorator (`CachingConfigurationService`) in the features layer — it is an application concern, not a domain one.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *N/A*     | —          | —                                   |
