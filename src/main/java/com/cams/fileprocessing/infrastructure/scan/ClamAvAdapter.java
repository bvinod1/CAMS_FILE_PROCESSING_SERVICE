package com.cams.fileprocessing.infrastructure.scan;

import com.cams.fileprocessing.business.scan.ScanOutcome;
import com.cams.fileprocessing.business.scan.ScanStatus;
import com.cams.fileprocessing.interfaces.VirusScanPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ClamAV adapter implementing {@link VirusScanPort} using the native INSTREAM TCP protocol.
 *
 * <p>This adapter communicates directly with the {@code clamd} daemon via a TCP socket.
 * No third-party ClamAV client library is used — the INSTREAM protocol is simple enough
 * to implement directly and this avoids additional dependency surface area.
 *
 * <h3>INSTREAM Protocol Summary</h3>
 * <ol>
 *   <li>Connect to clamd on the configured host:port</li>
 *   <li>Send the null-terminated command: {@code "zINSTREAM\0"}</li>
 *   <li>Send the file in chunks: 4-byte big-endian chunk length followed by chunk bytes</li>
 *   <li>Terminate with a 4-byte zero: {@code \0\0\0\0}</li>
 *   <li>Read the response line: {@code "stream: OK"} or {@code "stream: <virus> FOUND"}</li>
 * </ol>
 *
 * <h3>Profile Usage</h3>
 * This adapter is profile-agnostic. The only difference between environments is the
 * configured {@code cams.scan.host} and {@code cams.scan.port}:
 * <ul>
 *   <li><b>local</b>: ClamAV Docker container ({@code localhost:3310})</li>
 *   <li><b>gcp</b>: ClamAV GKE sidecar ({@code localhost:3310})</li>
 *   <li><b>aws</b>: ClamAV ECS sidecar ({@code localhost:3310})</li>
 * </ul>
 */
@Component
public class ClamAvAdapter implements VirusScanPort {

    private static final Logger log = LoggerFactory.getLogger(ClamAvAdapter.class);

    /** Maximum chunk size sent to clamd per INSTREAM write (10 MB). */
    private static final int CHUNK_SIZE = 10 * 1024 * 1024;

    /** Regex to extract the signature database date from the VERSION response. */
    private static final Pattern VERSION_DATE_PATTERN =
            Pattern.compile("/(\\d{4})-(\\d{2})-(\\d{2})");

    private final String host;
    private final int    port;
    private final int    timeoutSeconds;

    /**
     * Constructs the adapter using values from {@code application-{profile}.yml}.
     *
     * @param host           clamd hostname or IP (default: {@code localhost})
     * @param port           clamd TCP port (default: {@code 3310})
     * @param timeoutSeconds socket read timeout in seconds (default: {@code 120})
     */
    public ClamAvAdapter(
            @Value("${cams.scan.host:localhost}")           String host,
            @Value("${cams.scan.port:3310}")                int    port,
            @Value("${cams.scan.timeout-seconds:120}")      int    timeoutSeconds) {
        this.host           = host;
        this.port           = port;
        this.timeoutSeconds = timeoutSeconds;
        log.info("ClamAvAdapter initialised — daemon={}:{}, timeout={}s", host, port, timeoutSeconds);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Streams {@code content} to clamd using the INSTREAM command.
     * The file is never written to local disk or held entirely in memory.
     */
    @Override
    public ScanOutcome scan(String fileId, InputStream content) {
        log.info("Starting virus scan — fileId={}", fileId);
        long startMs = System.currentTimeMillis();

        String     engVersion  = "unknown";
        LocalDate  sigDate     = LocalDate.EPOCH;

        try {
            engVersion = engineVersion();
            sigDate    = signatureDate();
        } catch (Exception e) {
            log.warn("Could not retrieve ClamAV version info before scan — fileId={}", fileId, e);
        }

        try (Socket socket = openSocket()) {
            DataOutputStream out =
                    new DataOutputStream(socket.getOutputStream());

            // Send INSTREAM command (null-terminated)
            out.write("zINSTREAM\0".getBytes(StandardCharsets.UTF_8));

            // Stream file content in fixed-size chunks
            byte[] buffer = new byte[CHUNK_SIZE];
            int    bytesRead;
            while ((bytesRead = content.read(buffer)) > 0) {
                out.writeInt(bytesRead);
                out.write(buffer, 0, bytesRead);
            }
            // Terminate the stream
            out.writeInt(0);
            out.flush();

            // Read clamd response
            String response = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                    .readLine();

            long durationMs = System.currentTimeMillis() - startMs;
            log.debug("ClamAV response — fileId={}, response='{}', durationMs={}",
                    fileId, response, durationMs);

            return parseResponse(fileId, response, engVersion, sigDate, durationMs);

        } catch (IOException e) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("ClamAV scan failed — fileId={}, error={}", fileId, e.getMessage(), e);
            return ScanOutcome.error(fileId, e.getMessage(), engVersion, sigDate, durationMs);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends the {@code VERSION} command to clamd and returns the full version string.
     */
    @Override
    public String engineVersion() {
        return sendCommand("VERSION");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Parses the signature database date from the {@code VERSION} response.
     * The response format is: {@code ClamAV <version>/<sig-count>/<date>}
     * For example: {@code ClamAV 1.3.0/27380/Mon Mar 15 08:00:00 2026}
     */
    @Override
    public LocalDate signatureDate() {
        String version = sendCommand("VERSION");
        return parseSignatureDate(version);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Socket openSocket() throws IOException {
        Socket socket = new Socket(host, port);
        socket.setSoTimeout(timeoutSeconds * 1000);
        return socket;
    }

    private String sendCommand(String command) {
        try (Socket socket = openSocket()) {
            DataOutputStream out =
                    new DataOutputStream(socket.getOutputStream());
            out.write(("z" + command + "\0").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                    .readLine();
        } catch (IOException e) {
            log.warn("ClamAV command '{}' failed — {}:{} error={}", command, host, port, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    private ScanOutcome parseResponse(
            String fileId, String response,
            String engineVersion, LocalDate signatureDate,
            long durationMs) {

        if (response == null || response.trim().equalsIgnoreCase("stream: OK")) {
            log.info("Scan CLEAN — fileId={}", fileId);
            return ScanOutcome.clean(fileId, engineVersion, signatureDate, durationMs);
        }

        if (response.toUpperCase().contains("FOUND")) {
            // Response format: "stream: <VirusName> FOUND"
            String virusName = response
                    .replaceFirst("(?i)^stream:\\s*", "")
                    .replaceAll("(?i)\\s*FOUND$", "")
                    .trim();
            log.error("Scan INFECTED — fileId={}, virus={}", fileId, virusName);
            return ScanOutcome.infected(fileId, virusName, engineVersion, signatureDate, durationMs);
        }

        log.error("Scan ERROR — fileId={}, unexpectedResponse='{}'", fileId, response);
        return ScanOutcome.error(fileId, "Unexpected clamd response: " + response,
                engineVersion, signatureDate, durationMs);
    }

    /**
     * Parses the signature date from a VERSION response such as:
     * {@code ClamAV 1.3.0/27380/2026-03-15}
     * Older versions may emit a human-readable date; we attempt ISO-8601 first.
     */
    static LocalDate parseSignatureDate(String versionResponse) {
        if (versionResponse == null || versionResponse.startsWith("ERROR")) {
            return LocalDate.EPOCH;
        }
        // Try ISO-8601 yyyy-MM-dd embedded in the string
        Matcher m = VERSION_DATE_PATTERN.matcher(versionResponse);
        if (m.find()) {
            try {
                return LocalDate.of(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
            } catch (DateTimeParseException | NumberFormatException ex) {
                // fall through
            }
        }
        return LocalDate.EPOCH;
    }
}
