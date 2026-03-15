package com.cams.fileprocessing.common;

/**
 * Exhaustive enumeration of every valid lifecycle state for a {@code FileRecord}.
 *
 * <p>State transitions are governed by the Spring State Machine (Epic 5).
 * Until the state machine is wired, services must move between states by
 * setting the string value of this enum via {@link #name()}.
 *
 * <h3>Valid Transition Path</h3>
 * <pre>
 * AWAITING_UPLOAD
 *   └─► UPLOADED          (client confirms upload complete)
 *         └─► SCANNING    (scan worker picks up FileReceivedEvent)
 *               ├─► SCANNED_CLEAN   (ClamAV: OK)
 *               │     └─► VALIDATING
 *               │           ├─► VALIDATED
 *               │           │     └─► PROCESSING
 *               │           │           ├─► COMPLETED
 *               │           │           └─► PARTIALLY_COMPLETED
 *               │           └─► VALIDATION_FAILED ──► DLQ
 *               ├─► QUARANTINED     (ClamAV: virus found) ──► DLQ
 *               └─► SCAN_ERROR      (ClamAV unreachable)  ──► DLQ
 *
 * Any state ──► FAILED (unrecoverable error) ──► DLQ
 * </pre>
 */
public enum FileStatus {

    /** Pre-signed URL issued; waiting for the client to upload the file. */
    AWAITING_UPLOAD,

    /** Client has confirmed the upload is complete; file is in the quarantine bucket. */
    UPLOADED,

    /** ClamAV scan is in progress. */
    SCANNING,

    /** ClamAV scan completed — file is clean. Ready to proceed to validation. */
    SCANNED_CLEAN,

    /** ClamAV detected a virus or malware. File stays in quarantine; never moves. */
    QUARANTINED,

    /** ClamAV was unreachable or returned an unexpected response. Requires alert + retry/DLQ. */
    SCAN_ERROR,

    /** Header and structure validation is in progress. */
    VALIDATING,

    /** File structure is valid. Ready to proceed to record processing. */
    VALIDATED,

    /** File failed header/structure validation. See error_message for details. */
    VALIDATION_FAILED,

    /** Spring Batch record-level processing is in progress. */
    PROCESSING,

    /** All records processed successfully. Terminal success state. */
    COMPLETED,

    /**
     * Some records processed; others failed. Partial success.
     * Review record-level status for details.
     */
    PARTIALLY_COMPLETED,

    /** Unrecoverable error at any stage. Terminal failure state. */
    FAILED
}
