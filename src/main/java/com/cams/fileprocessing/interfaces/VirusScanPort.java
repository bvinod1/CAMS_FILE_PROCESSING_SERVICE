package com.cams.fileprocessing.interfaces;

import com.cams.fileprocessing.business.scan.ScanOutcome;

import java.io.InputStream;
import java.time.LocalDate;

/**
 * Port interface for virus/malware scanning.
 *
 * <p>All business logic that initiates a scan must go through this interface.
 * Direct use of ClamAV sockets, VirusTotal APIs, or any other scanning
 * library in business classes is strictly forbidden and enforced by ArchUnit.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@code ClamAvAdapter} — TCP INSTREAM protocol, used by all profiles
 *       (local Docker container, GCP/AWS sidecar). Host and port are
 *       configured per {@code application-{profile}.yml}.</li>
 * </ul>
 */
public interface VirusScanPort {

    /**
     * Scans the provided file content for viruses or malware.
     *
     * <p>The implementation must stream content to the scanner in chunks and
     * must never load the entire file into memory.
     *
     * @param fileId  the unique identifier of the file being scanned (for logging/tracing)
     * @param content the file content as an {@link InputStream}; the caller retains
     *                responsibility for closing the stream
     * @return a {@link ScanOutcome} describing whether the file is clean, infected, or errored
     */
    ScanOutcome scan(String fileId, InputStream content);

    /**
     * Returns the current ClamAV engine version string (e.g. {@code "ClamAV 1.3.0"}).
     *
     * @return non-blank engine version
     */
    String engineVersion();

    /**
     * Returns the date of the currently loaded virus signature database.
     *
     * @return signature date; implementations should alert if this is more than 24 hours old
     */
    LocalDate signatureDate();
}
