package com.cams.fileprocessing.features.validation.models;

import java.time.Instant;
import java.util.List;

/**
 * Immutable snapshot of a validation template as loaded from the store.
 *
 * <p>Instances are cached by {@code CachingValidationTemplateService} (60 s TTL).
 *
 * @param id          surrogate primary key (UUID, assigned by DB)
 * @param flowType    the file-flow category this template applies to (e.g. {@code "NAV", "TRANSACTION"})
 * @param version     monotonically increasing version counter, incremented on every PUT
 * @param columnRules ordered list of column rules; order determines expected header position
 * @param active      {@code true} if this template is the current active version for the flowType
 * @param createdAt   timestamp when this template version was created
 */
public record ValidationTemplate(
        String id,
        String flowType,
        int version,
        List<ColumnRule> columnRules,
        boolean active,
        Instant createdAt
) {
    /**
     * Returns the column rule at position {@code pos}, or {@code null} if no rule exists there.
     */
    public ColumnRule ruleAt(int pos) {
        return columnRules.stream()
                .filter(r -> r.position() == pos)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the expected number of columns (max position + 1).
     */
    public int expectedColumnCount() {
        return columnRules.stream()
                .mapToInt(r -> r.position() + 1)
                .max()
                .orElse(0);
    }
}
