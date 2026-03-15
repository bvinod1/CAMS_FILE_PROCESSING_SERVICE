package com.cams.fileprocessing.business.scan;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable result of a single virus scan operation.
 *
 * <p>This record is the return value of {@link com.cams.fileprocessing.interfaces.VirusScanPort#scan}
 * and is persisted to the {@code scan_results} table. Once inserted, scan records are never
 * updated or deleted (enforced at the database level).
 *
 * @param fileId         the ID of the file that was scanned
 * @param status         overall scan result — {@link ScanStatus#CLEAN}, {@link ScanStatus#INFECTED},
 *                       or {@link ScanStatus#ERROR}
 * @param virusName      name of the detected virus/malware (null when status is CLEAN)
 * @param engineVersion  ClamAV engine version used for this scan (e.g. {@code "ClamAV 1.3.0"})
 * @param signatureDate  date of the virus signature database used for this scan
 * @param durationMs     wall-clock duration of the scan operation in milliseconds
 * @param scannedAt      timestamp when the scan completed
 * @param errorDetail    human-readable error description (null unless status is ERROR)
 */
public record ScanOutcome(
        String     fileId,
        ScanStatus status,
        String     virusName,
        String     engineVersion,
        LocalDate  signatureDate,
        long       durationMs,
        Instant    scannedAt,
        String     errorDetail
) {

    /**
     * Convenience factory for a clean scan result.
     */
    public static ScanOutcome clean(
            String fileId,
            String engineVersion,
            LocalDate signatureDate,
            long durationMs) {
        return new ScanOutcome(fileId, ScanStatus.CLEAN, null,
                engineVersion, signatureDate, durationMs, Instant.now(), null);
    }

    /**
     * Convenience factory for an infected scan result.
     */
    public static ScanOutcome infected(
            String fileId,
            String virusName,
            String engineVersion,
            LocalDate signatureDate,
            long durationMs) {
        return new ScanOutcome(fileId, ScanStatus.INFECTED, virusName,
                engineVersion, signatureDate, durationMs, Instant.now(), null);
    }

    /**
     * Convenience factory for a scan that could not complete due to an error.
     */
    public static ScanOutcome error(
            String fileId,
            String errorDetail,
            String engineVersion,
            LocalDate signatureDate,
            long durationMs) {
        return new ScanOutcome(fileId, ScanStatus.ERROR, null,
                engineVersion, signatureDate, durationMs, Instant.now(), errorDetail);
    }
}
