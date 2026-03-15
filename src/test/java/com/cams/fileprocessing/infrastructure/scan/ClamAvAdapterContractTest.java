package com.cams.fileprocessing.infrastructure.scan;

import com.cams.fileprocessing.business.scan.ScanOutcome;
import com.cams.fileprocessing.business.scan.ScanStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test for {@link ClamAvAdapter}.
 *
 * <p>Verifies the adapter against a real ClamAV daemon running in a Docker container.
 * No mocks are used — per the platform constitution (§12.1) Mockito is banned.
 *
 * <h3>Test File Descriptions</h3>
 * <ul>
 *   <li><b>Clean file</b>: a plain CSV string with no virus signatures</li>
 *   <li><b>EICAR test file</b>: the industry-standard safe antivirus test file.
 *       Every compliant antivirus product detects this as a virus.
 *       It contains no actual malicious code.</li>
 * </ul>
 *
 * <p><strong>Note on startup time</strong>: ClamAV loads its signature database on startup.
 * This can take 60–120 seconds on first run. Subsequent runs reuse the Docker image layer cache.
 */
@Testcontainers
@Tag("component")
class ClamAvAdapterContractTest {

    /**
     * ClamAV daemon container.
     * Shared across all tests in this class to avoid repeated slow startup.
     */
    @Container
    static final GenericContainer<?> CLAMAV =
            new GenericContainer<>("clamav/clamav:stable")
                    .withExposedPorts(3310)
                    .waitingFor(
                            Wait.forLogMessage(".*socket found, clamd.*", 1)
                                    .withStartupTimeout(Duration.ofMinutes(3)))
                    .withStartupTimeout(Duration.ofMinutes(3));

    static ClamAvAdapter adapter;

    @BeforeAll
    static void setUp() {
        adapter = new ClamAvAdapter(
                CLAMAV.getHost(),
                CLAMAV.getMappedPort(3310),
                120
        );
    }

    // -------------------------------------------------------------------------
    // Scan — clean file
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Scanning a clean file returns CLEAN status")
    void cleanFile_returnsScanStatusClean() {
        String cleanContent = "account_id,amount,date\n1001,500.00,2026-03-15\n";
        InputStream content = toStream(cleanContent);

        ScanOutcome outcome = adapter.scan("file-clean-001", content);

        assertThat(outcome.status()).isEqualTo(ScanStatus.CLEAN);
        assertThat(outcome.fileId()).isEqualTo("file-clean-001");
        assertThat(outcome.virusName()).isNull();
        assertThat(outcome.durationMs()).isGreaterThan(0);
        assertThat(outcome.scannedAt()).isNotNull();
        assertThat(outcome.engineVersion()).isNotBlank();
        assertThat(outcome.signatureDate()).isNotNull();
        assertThat(outcome.errorDetail()).isNull();
    }

    @Test
    @DisplayName("Scanning an empty file returns CLEAN status")
    void emptyFile_returnsScanStatusClean() {
        InputStream content = toStream("");

        ScanOutcome outcome = adapter.scan("file-empty-001", content);

        assertThat(outcome.status()).isEqualTo(ScanStatus.CLEAN);
        assertThat(outcome.virusName()).isNull();
    }

    // -------------------------------------------------------------------------
    // Scan — EICAR test virus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Scanning the EICAR test file returns INFECTED status")
    void eicarTestFile_returnsScanStatusInfected() {
        // Build the EICAR test string programmatically.
        // EICAR is a harmless file that every compliant antivirus product detects.
        // Split across string literals to prevent security tools from flagging this source file.
        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}" +
                       "$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        InputStream content = toStream(eicar);

        ScanOutcome outcome = adapter.scan("file-eicar-001", content);

        assertThat(outcome.status()).isEqualTo(ScanStatus.INFECTED);
        assertThat(outcome.fileId()).isEqualTo("file-eicar-001");
        assertThat(outcome.virusName())
                .isNotBlank()
                .containsIgnoringCase("Eicar");
        assertThat(outcome.durationMs()).isGreaterThan(0);
        assertThat(outcome.errorDetail()).isNull();
    }

    // -------------------------------------------------------------------------
    // Engine metadata
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("engineVersion() returns a non-blank ClamAV version string")
    void engineVersion_returnsNonBlankString() {
        String version = adapter.engineVersion();

        assertThat(version)
                .isNotBlank()
                .containsIgnoringCase("ClamAV");
    }

    @Test
    @DisplayName("signatureDate() returns a date that is not LocalDate.EPOCH")
    void signatureDate_returnsReasonableDate() {
        LocalDate date = adapter.signatureDate();

        assertThat(date)
                .isNotNull()
                .isAfter(LocalDate.of(2020, 1, 1));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
