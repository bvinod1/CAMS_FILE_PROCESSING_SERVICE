package com.cams.fileprocessing.features.validation;

import com.cams.fileprocessing.features.validation.models.ValidationResultStatus;

import java.time.Instant;

/**
 * Published after a file's header has been validated and the result is persisted.
 * Published only AFTER the {@code validation_results} row is committed (transactional outbox).
 *
 * @param fileId          the file that was validated
 * @param flowType        the flow type used for template selection
 * @param result          PASS, FAIL, or UNPARSEABLE_HEADER
 * @param templateVersion the template version used for this validation
 * @param errorCount      number of validation errors found (0 for PASS)
 * @param validatedAt     timestamp of the validation completion
 */
public record ValidationCompletedEvent(
        String fileId,
        String flowType,
        ValidationResultStatus result,
        int templateVersion,
        int errorCount,
        Instant validatedAt
) {}
