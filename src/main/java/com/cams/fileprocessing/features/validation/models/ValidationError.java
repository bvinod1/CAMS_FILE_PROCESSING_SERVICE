package com.cams.fileprocessing.features.validation.models;

/**
 * A single field-level validation error captured during header validation.
 *
 * @param columnName      the column name the error relates to (may be {@code null} for UNPARSEABLE_HEADER)
 * @param position        the zero-indexed position in the header row (may be {@code -1} if unknown)
 * @param errorCode       the specific error code
 * @param detail          human-readable description of the problem
 */
public record ValidationError(
        String columnName,
        int position,
        ValidationErrorCode errorCode,
        String detail
) {
    public static ValidationError of(ValidationErrorCode code, String columnName,
                                     int position, String detail) {
        return new ValidationError(columnName, position, code, detail);
    }
}
