package com.cams.fileprocessing.features.validation.models;

/**
 * Error codes produced by the header validation service.
 */
public enum ValidationErrorCode {

    /** A required column is not present in the header row. */
    MISSING_COLUMN,

    /** A column is present but at a different position than expected. */
    WRONG_POSITION,

    /** The header row contains a column that is not defined in the template. */
    EXTRA_COLUMN,

    /** The header row cannot be parsed (binary/encoding error or empty file). */
    UNPARSEABLE_HEADER,

    /** A value in the header row is null/blank when the rule requires non-null. */
    NULL_NOT_ALLOWED,

    /** A column value does not match the expected data type. */
    INVALID_DATA_TYPE
}
