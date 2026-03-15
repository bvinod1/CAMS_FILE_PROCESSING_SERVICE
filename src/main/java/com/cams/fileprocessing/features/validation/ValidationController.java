package com.cams.fileprocessing.features.validation;

import com.cams.fileprocessing.interfaces.ValidationResultPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller exposing validation query endpoints.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /api/v1/uploads/{fileId}/validation-errors} — returns validation errors
 *       for the given file. Returns {@code 404} if no validation result exists yet.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class ValidationController {

    private static final Logger log = LoggerFactory.getLogger(ValidationController.class);

    private final ValidationResultPort validationResultPort;

    public ValidationController(ValidationResultPort validationResultPort) {
        this.validationResultPort = validationResultPort;
    }

    /**
     * Returns the validation result and all field-level errors for the given file.
     *
     * <p>Returns {@code 404} if no validation run has been completed for this file yet.
     * Returns {@code 200} with an empty {@code errors} array if validation passed.
     *
     * @param fileId the unique file identifier
     * @return validation result response or 404
     */
    @GetMapping("/{fileId}/validation-errors")
    public Mono<ResponseEntity<ValidationErrorResponse>> getValidationErrors(
            @PathVariable String fileId) {
        log.info("Validation errors requested for fileId={}", fileId);
        return validationResultPort.findLatestByFileId(fileId)
                .map(result -> ResponseEntity.ok(ValidationErrorResponse.from(result)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
