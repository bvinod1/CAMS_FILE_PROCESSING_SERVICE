package com.cams.fileprocessing.business.validation;

import com.cams.fileprocessing.features.validation.models.*;
import com.cams.fileprocessing.interfaces.ObjectStoragePort;
import com.cams.fileprocessing.interfaces.ValidationTemplatePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business-layer service that validates the header row of a file against a {@link ValidationTemplate}.
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li>Only the first line of the file is read — the entire file is never loaded into memory (FR-302).</li>
 *   <li>All column errors are collected in a single pass — no fail-fast behaviour (NFR-304).</li>
 *   <li>File reading runs on {@code Schedulers.boundedElastic()} to avoid blocking the event loop (FR-306).</li>
 *   <li>Only {@link ObjectStoragePort} and {@link ValidationTemplatePort} (interfaces) are imported —
 *       never a cloud-SDK or JPA class (ArchUnit-enforced).</li>
 * </ul>
 *
 * <h3>Error Codes</h3>
 * <ul>
 *   <li>UNPARSEABLE_HEADER — header row unreadable (binary, encoding error, empty file)</li>
 *   <li>MISSING_COLUMN     — required column absent from header</li>
 *   <li>WRONG_POSITION     — column present but at unexpected position</li>
 *   <li>EXTRA_COLUMN       — position contains a column not defined in the template</li>
 *   <li>NULL_NOT_ALLOWED   — header cell is blank at a position expected to have a name</li>
 * </ul>
 */
@Service
@Profile({"local", "gcp"})
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    /** Maximum bytes to read when streaming the first line (1 MB — prevents runaway reads). */
    private static final long MAX_FIRST_LINE_BYTES = 1_048_576L;

    private final ValidationTemplatePort templatePort;
    private final ObjectStoragePort objectStoragePort;

    public ValidationService(ValidationTemplatePort templatePort,
                             ObjectStoragePort objectStoragePort) {
        this.templatePort = templatePort;
        this.objectStoragePort = objectStoragePort;
    }

    /**
     * Validates the header row of the file at {@code bucket/objectPath} against the active
     * template for {@code flowType}.
     *
     * @param fileId     file identifier (for logging and result)
     * @param flowType   flow type used to select the validation template
     * @param bucket     object storage bucket containing the file
     * @param objectPath object key / path within the bucket
     * @return a {@link Mono} emitting the {@link ValidationResult}
     */
    public Mono<ValidationResult> validate(String fileId, String flowType,
                                           String bucket, String objectPath) {
        long startMs = System.currentTimeMillis();
        log.info("Starting header validation — fileId={}, flowType={}", fileId, flowType);

        return templatePort.getActiveTemplate(flowType)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "No active validation template found for flowType=" + flowType)))
                .flatMap(template ->
                        readFirstLine(bucket, objectPath)
                                .map(headerLine -> runValidation(fileId, flowType, template,
                                        headerLine, startMs))
                                .onErrorResume(IOException.class, ex -> {
                                    log.warn("Header unreadable — fileId={}, error={}",
                                            fileId, ex.getMessage());
                                    long durationMs = System.currentTimeMillis() - startMs;
                                    return Mono.just(ValidationResult.unparseable(fileId, flowType,
                                            template.version(), ex.getMessage(), durationMs));
                                })
                )
                .doOnNext(result -> log.info(
                        "Validation complete — fileId={}, status={}, errors={}, durationMs={}",
                        fileId, result.status(), result.errors().size(), result.durationMs()));
    }

    // ---------- private helpers ----------

    /**
     * Reads the first non-empty line from the object as a UTF-8 string.
     * Reads at most {@link #MAX_FIRST_LINE_BYTES} before throwing.
     */
    private Mono<String> readFirstLine(String bucket, String objectPath) {
        return objectStoragePort.readFirstBytes(bucket, objectPath, MAX_FIRST_LINE_BYTES)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(inputStream -> Mono.fromCallable(() -> {
                    try (BufferedReader reader =
                                 new BufferedReader(new InputStreamReader(inputStream,
                                         StandardCharsets.UTF_8))) {
                        String line = reader.readLine();
                        if (line == null || line.isBlank()) {
                            throw new IOException("File is empty or first line is blank");
                        }
                        return line.trim();
                    }
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * Runs the full, single-pass column validation on the parsed header line.
     * Collects ALL errors before returning (no fail-fast).
     */
    private ValidationResult runValidation(String fileId, String flowType,
                                           ValidationTemplate template,
                                           String headerLine, long startMs) {
        List<ValidationError> errors = new ArrayList<>();
        long durationMs = System.currentTimeMillis() - startMs;

        // Split on comma (CSV convention); trim each token
        String[] rawColumns = headerLine.split(",", -1);
        String[] actualColumns = new String[rawColumns.length];
        for (int i = 0; i < rawColumns.length; i++) {
            actualColumns[i] = rawColumns[i].trim();
        }

        // Build map: name → actual position (for WRONG_POSITION detection)
        Map<String, Integer> actualPositionByName = new HashMap<>();
        for (int i = 0; i < actualColumns.length; i++) {
            String col = actualColumns[i];
            if (col.isEmpty()) {
                // NULL_NOT_ALLOWED — blank column name at this position
                errors.add(ValidationError.of(
                        ValidationErrorCode.NULL_NOT_ALLOWED,
                        "(blank)", i,
                        "Header cell at position " + i + " is blank"));
            } else {
                actualPositionByName.put(col, i);
            }
        }

        // ---- Check 1: required columns (MISSING_COLUMN + WRONG_POSITION) ----
        for (ColumnRule rule : template.columnRules()) {
            if (!rule.required()) continue;

            Integer actualPos = actualPositionByName.get(rule.name());
            if (actualPos == null) {
                errors.add(ValidationError.of(
                        ValidationErrorCode.MISSING_COLUMN,
                        rule.name(), rule.position(),
                        "Required column '" + rule.name() + "' not found in header"));
            } else if (actualPos != rule.position()) {
                errors.add(ValidationError.of(
                        ValidationErrorCode.WRONG_POSITION,
                        rule.name(), actualPos,
                        "Column '" + rule.name() + "' found at position " + actualPos
                                + " but expected at position " + rule.position()));
            }
        }

        // ---- Check 2: extra columns (EXTRA_COLUMN) ----
        for (int i = 0; i < actualColumns.length; i++) {
            String col = actualColumns[i];
            if (col.isEmpty()) continue; // already reported as NULL_NOT_ALLOWED
            ColumnRule expectedRule = template.ruleAt(i);
            if (expectedRule == null) {
                errors.add(ValidationError.of(
                        ValidationErrorCode.EXTRA_COLUMN,
                        col, i,
                        "Column '" + col + "' at position " + i
                                + " is not defined in the template for flowType=" + flowType));
            }
        }

        durationMs = System.currentTimeMillis() - startMs;
        if (errors.isEmpty()) {
            return ValidationResult.pass(fileId, flowType, template.version(), durationMs);
        }
        return ValidationResult.fail(fileId, flowType, template.version(), errors, durationMs);
    }
}
