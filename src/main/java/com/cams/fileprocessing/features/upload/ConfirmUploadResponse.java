package com.cams.fileprocessing.features.upload;

import java.time.Instant;

/**
 * Response returned when a client confirms that their file upload is complete.
 *
 * @param fileId       the unique identifier of the file record
 * @param status       the new status of the file record (always {@code "UPLOADED"} on success)
 * @param confirmedAt  timestamp of the confirmation
 */
public record ConfirmUploadResponse(
        String  fileId,
        String  status,
        Instant confirmedAt
) {}
