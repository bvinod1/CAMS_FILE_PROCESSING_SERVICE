package com.cams.fileprocessing.features.validation.models;

/**
 * Outcome of a header validation run for a single file.
 */
public enum ValidationResultStatus {

    /** All required columns present in the correct positions — file may proceed. */
    PASS,

    /** One or more column rule violations were found — file is rejected. */
    FAIL,

    /** The header row could not be parsed (binary, encoding error, empty file). */
    UNPARSEABLE_HEADER
}
