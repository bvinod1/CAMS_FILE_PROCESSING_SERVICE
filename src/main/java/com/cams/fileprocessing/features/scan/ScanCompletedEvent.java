package com.cams.fileprocessing.features.scan;

import com.cams.fileprocessing.business.scan.ScanStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain event published when the ClamAV scan of a file has completed.
 *
 * <p>Downstream consumers:
 * <ul>
 *   <li><b>Validation Worker (Epic 3)</b> — consumes events where {@code result = CLEAN}</li>
 *   <li><b>Alerting Service</b> — consumes events where {@code result = INFECTED}</li>
 * </ul>
 *
 * @param eventId        unique identifier for this event occurrence (UUID)
 * @param fileId         the file that was scanned
 * @param flowType       flow type of the file (e.g. NAV, TRANSACTION) — needed by ValidationWorker
 * @param bucket         object-storage bucket where the clean file now resides
 * @param objectPath     object key / GCS path of the file in the clean bucket
 * @param result         scan outcome — CLEAN, INFECTED, or ERROR
 * @param virusName      name of detected virus (null if result is not INFECTED)
 * @param engineVersion  ClamAV engine version used
 * @param signatureDate  date of the virus signature database used
 * @param scanDurationMs wall-clock duration of the scan in milliseconds
 * @param occurredAt     timestamp when the scan completed
 */
public record ScanCompletedEvent(
        String     eventId,
        String     fileId,
        String     flowType,
        String     bucket,
        String     objectPath,
        ScanStatus result,
        String     virusName,
        String     engineVersion,
        LocalDate  signatureDate,
        long       scanDurationMs,
        Instant    occurredAt
) {}
