package com.cams.fileprocessing.business.scan;

/**
 * Result of a ClamAV virus scan.
 *
 * <ul>
 *   <li>{@link #CLEAN}    — file was scanned and no threats were found</li>
 *   <li>{@link #INFECTED} — a virus or malware signature was matched</li>
 *   <li>{@link #ERROR}    — the scanner was unreachable or returned an unexpected response</li>
 * </ul>
 */
public enum ScanStatus {

    /** File passed the virus scan; safe to proceed to validation. */
    CLEAN,

    /** Virus or malware detected; file must remain in quarantine. */
    INFECTED,

    /**
     * ClamAV was unreachable, timed out, or returned an unrecognisable response.
     * The file must NOT be processed. An alert must be raised.
     */
    ERROR
}
