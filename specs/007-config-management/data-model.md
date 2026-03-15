# Data Model — Epic 7: Configuration Management

## New Tables

### `config_templates` (processing parameters)

Stores chunk size, retry, and threading configuration per flow type.

```sql
CREATE TABLE config_templates (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    flow_type       VARCHAR(50)     NOT NULL UNIQUE,
    chunk_size      INTEGER         NOT NULL DEFAULT 500,
    thread_pool_size INTEGER        NOT NULL DEFAULT 4,
    retry_max_attempts INTEGER      NOT NULL DEFAULT 3,
    retry_backoff_ms JSONB          NOT NULL DEFAULT '[1000, 2000, 4000]',
    external_api_url VARCHAR(500),
    external_api_timeout_ms INTEGER NOT NULL DEFAULT 5000,
    version         INTEGER         NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      VARCHAR(100)    NOT NULL,
    updated_by      VARCHAR(100)    NOT NULL,
    CONSTRAINT pk_config_templates PRIMARY KEY (id)
);
```

---

### `feature_flags`

Runtime feature toggles per flow type.

```sql
CREATE TABLE feature_flags (
    flow_type               VARCHAR(50)     NOT NULL,
    scanning_enabled        BOOLEAN         NOT NULL DEFAULT TRUE,
    validation_enabled      BOOLEAN         NOT NULL DEFAULT TRUE,
    processing_enabled      BOOLEAN         NOT NULL DEFAULT TRUE,
    strict_header_mode      BOOLEAN         NOT NULL DEFAULT FALSE,
    pii_redaction_enabled   BOOLEAN         NOT NULL DEFAULT TRUE,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by              VARCHAR(100)    NOT NULL,
    CONSTRAINT pk_feature_flags PRIMARY KEY (flow_type)
);
```

---

### `priority_rules`

Maps flow types to priority levels.

```sql
CREATE TABLE priority_rules (
    flow_type       VARCHAR(50)     NOT NULL,
    priority_level  INTEGER         NOT NULL,   -- 0 = P0 (highest), 1 = P1, 2 = P2
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by      VARCHAR(100)    NOT NULL,
    CONSTRAINT pk_priority_rules PRIMARY KEY (flow_type),
    CONSTRAINT chk_priority_level CHECK (priority_level IN (0, 1, 2))
);
```

---

### `config_audit`

Immutable audit log for all config changes.

```sql
CREATE TABLE config_audit (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    table_name      VARCHAR(100)    NOT NULL,
    flow_type       VARCHAR(50),
    changed_by      VARCHAR(100)    NOT NULL,
    change_type     VARCHAR(20)     NOT NULL,   -- CREATE | UPDATE | DELETE
    old_value       JSONB,
    new_value       JSONB           NOT NULL,
    changed_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT pk_config_audit PRIMARY KEY (id)
);

-- INSERT-only
REVOKE UPDATE, DELETE ON config_audit FROM cams_app;
```

---

## `ConfigurationPort` Interface

```java
package com.cams.fileprocessing.interfaces;

public interface ConfigurationPort {
    /** Returns the processing config for a flow type, using cache if available. */
    ProcessingConfig getProcessingConfig(String flowType);

    /** Returns feature flags for a flow type. */
    FeatureFlags getFeatureFlags(String flowType);

    /** Returns the priority level (0, 1, or 2) for a flow type. */
    int getPriorityLevel(String flowType);

    /** Updates feature flags and invalidates the cache. */
    void updateFeatureFlags(String flowType, FeatureFlags flags, String actor);

    /** Updates processing config and increments version. */
    void updateProcessingConfig(String flowType, ProcessingConfig config, String actor);

    /** Updates priority mapping. */
    void updatePriorityRule(String flowType, int priorityLevel, String actor);
}
```

---

## Cache Design

| Cache Name | Key | Value Type | TTL | Eviction On |
|---|---|---|---|---|
| `processingConfigCache` | `flowType` | `ProcessingConfig` | 60s | Config UPDATE API call |
| `featureFlagsCache` | `flowType` | `FeatureFlags` | 30s | Flag UPDATE API call |
| `priorityRulesCache` | `flowType` | `Integer` | 30s | Priority UPDATE API call |

Implementation: `CaffeineCache` via Spring `@Cacheable` + `@CacheEvict`.

---

## Seed Data (Local Profile — `db/init/03-config-seed.sql`)

```sql
-- Processing config
INSERT INTO config_templates (id, flow_type, chunk_size, thread_pool_size,
    retry_max_attempts, retry_backoff_ms, created_by, updated_by)
VALUES
    (gen_random_uuid(), 'NAV',         100, 8, 3, '[1000,2000,4000]', 'seed', 'seed'),
    (gen_random_uuid(), 'TRANSACTION', 500, 4, 3, '[1000,2000,4000]', 'seed', 'seed');

-- Feature flags
INSERT INTO feature_flags (flow_type, scanning_enabled, validation_enabled,
    processing_enabled, updated_by)
VALUES
    ('NAV',         TRUE, TRUE, TRUE, 'seed'),
    ('TRANSACTION', TRUE, TRUE, TRUE, 'seed');

-- Priority rules
INSERT INTO priority_rules (flow_type, priority_level, updated_by)
VALUES
    ('NAV',         0, 'seed'),   -- P0
    ('TRANSACTION', 1, 'seed');   -- P1
```

---

## IAM / Access Control

| Role | Tables | Access |
|---|---|---|
| `cams_app` | `config_templates`, `feature_flags`, `priority_rules` | SELECT, INSERT, UPDATE |
| `cams_app` | `config_audit` | SELECT, INSERT only |
| `cams_readonly` | all | SELECT only |
