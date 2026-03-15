package com.cams.fileprocessing.features.validation;

import com.cams.fileprocessing.features.validation.models.ValidationErrorCode;
import com.cams.fileprocessing.features.validation.models.ValidationResult;
import com.cams.fileprocessing.features.validation.models.ValidationResultStatus;

import java.time.Instant;
import java.util.List;

/**
 * Response body for {@code GET /api/v1/uploads/{fileId}/validation-errors}.
 */
public record ValidationErrorResponse(
        String fileId,
        String flowType,
        int templateVersion,
        ValidationResultStatus status,
        int errorCount,
        Instant validatedAt,
        List<ErrorDetail> errors
) {
    public record ErrorDetail(
            String columnName,
            int position,
            ValidationErrorCode errorCode,
            String detail
    ) {}

    /** Maps a domain {@link ValidationResult} to this response. */
    public static ValidationErrorResponse from(ValidationResult r) {
        List<ErrorDetail> details = r.errors().stream()
                .map(e -> new ErrorDetail(e.columnName(), e.position(), e.errorCode(), e.detail()))
                .toList();
        return new ValidationErrorResponse(
                r.fileId(), r.flowType(), r.templateVersion(),
                r.status(), r.errors().size(), r.validatedAt(), details);
    }
}
