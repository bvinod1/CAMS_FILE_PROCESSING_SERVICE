package com.cams.fileprocessing.features.validation.models;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of a header validation run for a single file.
 *
 * @param fileId           the file being validated
 * @param flowType         file flow type used to select the template
 * @param templateVersion  version of the template that was applied
 * @param status           overall outcome: PASS, FAIL, or UNPARSEABLE_HEADER
 * @param errors           list of field-level errors (empty if status is PASS)
 * @param durationMs       elapsed time for the validation run in milliseconds
 * @param validatedAt      timestamp when validation completed
 */
public record ValidationResult(
        String fileId,
        String flowType,
        int templateVersion,
        ValidationResultStatus status,
        List<ValidationError> errors,
        long durationMs,
        Instant validatedAt
) {
    /** Convenience factory for a passing result. */
    public static ValidationResult pass(String fileId, String flowType,
                                        int templateVersion, long durationMs) {
        return new ValidationResult(fileId, flowType, templateVersion,
                ValidationResultStatus.PASS, List.of(), durationMs, Instant.now());
    }

    /** Convenience factory for a failed result. */
    public static ValidationResult fail(String fileId, String flowType,
                                        int templateVersion, List<ValidationError> errors,
                                        long durationMs) {
        return new ValidationResult(fileId, flowType, templateVersion,
                ValidationResultStatus.FAIL, errors, durationMs, Instant.now());
    }

    /** Convenience factory for an unparseable header. */
    public static ValidationResult unparseable(String fileId, String flowType,
                                               int templateVersion, String reason,
                                               long durationMs) {
        return new ValidationResult(fileId, flowType, templateVersion,
                ValidationResultStatus.UNPARSEABLE_HEADER,
                List.of(ValidationError.of(ValidationErrorCode.UNPARSEABLE_HEADER,
                        null, -1, reason)),
                durationMs, Instant.now());
    }
}
