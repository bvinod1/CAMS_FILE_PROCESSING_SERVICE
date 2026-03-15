package com.cams.fileprocessing.features.validation;

import com.cams.fileprocessing.features.validation.models.ColumnDataType;
import com.cams.fileprocessing.features.validation.models.ValidationTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Response body for validation template endpoints.
 */
public record ValidationTemplateResponse(
        String id,
        String flowType,
        int version,
        boolean active,
        Instant createdAt,
        List<ColumnRuleResponse> columnRules
) {
    public record ColumnRuleResponse(
            String       name,
            int          position,
            ColumnDataType dataType,
            boolean      required,
            boolean      allowNull,
            Integer      maxLength,
            String       pattern
    ) {}

    /** Maps a domain {@link ValidationTemplate} to this response. */
    public static ValidationTemplateResponse from(ValidationTemplate t) {
        List<ColumnRuleResponse> rules = t.columnRules().stream()
                .map(r -> new ColumnRuleResponse(
                        r.name(), r.position(), r.dataType(),
                        r.required(), r.allowNull(), r.maxLength(), r.pattern()))
                .toList();
        return new ValidationTemplateResponse(
                t.id(), t.flowType(), t.version(), t.active(), t.createdAt(), rules);
    }
}
