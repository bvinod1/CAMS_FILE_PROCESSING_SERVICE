package com.cams.fileprocessing.features.validation;

import com.cams.fileprocessing.features.validation.models.ColumnDataType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/validation-templates}
 * and {@code PUT /api/v1/validation-templates/{flowType}}.
 */
public record ValidationTemplateRequest(
        @NotBlank String flowType,
        @NotEmpty List<ColumnRuleRequest> columnRules
) {
    public record ColumnRuleRequest(
            @NotBlank String name,
            @Min(0)   int position,
            @NotNull  ColumnDataType dataType,
                      boolean required,
                      boolean allowNull,
                      Integer maxLength,
                      String  pattern
    ) {}
}
