package com.cams.fileprocessing.interfaces;

import com.cams.fileprocessing.features.validation.models.ValidationError;
import com.cams.fileprocessing.features.validation.models.ValidationResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port interface for reading persisted validation results and errors.
 *
 * <p>Separates read (query) operations from the write path in {@link ValidationTemplatePort}.
 * Local adapter: JPA/PostgreSQL. GCP adapter: Cloud Spanner (Epic 6).
 */
public interface ValidationResultPort {

    /**
     * Returns the most recent validation result for the given file, or empty if none exists.
     */
    Mono<ValidationResult> findLatestByFileId(String fileId);

    /**
     * Returns all validation errors for the given file's most recent validation run.
     */
    Flux<ValidationError> findErrorsByFileId(String fileId);
}
