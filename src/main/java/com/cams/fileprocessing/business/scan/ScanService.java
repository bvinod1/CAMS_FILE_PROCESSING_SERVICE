package com.cams.fileprocessing.business.scan;

import com.cams.fileprocessing.interfaces.VirusScanPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;

/**
 * Business-layer service that orchestrates virus scanning via {@link VirusScanPort}.
 *
 * <p>This service is the single entry point for all scan operations.
 * It has <strong>no knowledge of</strong> storage adapters, messaging, or HTTP —
 * those concerns are handled in the {@code features/scan} package (the Scan Worker).
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li>ClamAV scanning is inherently blocking (TCP socket I/O). The blocking call is
 *       delegated to {@code Schedulers.boundedElastic()} so that the reactive event-loop
 *       threads are never blocked.</li>
 *   <li>Only the {@link VirusScanPort} interface is imported from {@code infrastructure} —
 *       never a concrete adapter class. ArchUnit enforces this boundary.</li>
 * </ul>
 */
@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final VirusScanPort virusScanPort;

    /**
     * @param virusScanPort the active {@link VirusScanPort} adapter, injected by Spring.
     *                      The concrete implementation is selected by the active Spring profile.
     */
    public ScanService(VirusScanPort virusScanPort) {
        this.virusScanPort = virusScanPort;
    }

    /**
     * Scans the given file content for viruses or malware.
     *
     * <p>The blocking ClamAV socket call is executed on the bounded-elastic scheduler
     * to avoid blocking the reactive event loop.
     *
     * @param fileId  the unique identifier of the file (used for logging and the returned outcome)
     * @param content the file content as an {@link InputStream}
     * @return a {@link Mono} that emits a {@link ScanOutcome} when the scan completes
     */
    public Mono<ScanOutcome> scan(String fileId, InputStream content) {
        log.info("Initiating scan — fileId={}", fileId);
        return Mono
                .fromCallable(() -> virusScanPort.scan(fileId, content))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(outcome ->
                        log.info("Scan complete — fileId={}, result={}, durationMs={}",
                                fileId, outcome.status(), outcome.durationMs()))
                .doOnError(ex ->
                        log.error("Scan threw an unexpected exception — fileId={}", fileId, ex));
    }

    /**
     * Returns the current ClamAV engine version string.
     *
     * @return engine version as reported by the ClamAV daemon
     */
    public String engineVersion() {
        return virusScanPort.engineVersion();
    }

    /**
     * Returns the date of the currently loaded virus signature database.
     *
     * @return signature date
     */
    public java.time.LocalDate signatureDate() {
        return virusScanPort.signatureDate();
    }
}
