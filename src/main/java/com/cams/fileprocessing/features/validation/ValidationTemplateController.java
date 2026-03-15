package com.cams.fileprocessing.features.validation;

import com.cams.fileprocessing.features.validation.models.ColumnDataType;
import com.cams.fileprocessing.features.validation.models.ColumnRule;
import com.cams.fileprocessing.features.validation.models.ValidationTemplate;
import com.cams.fileprocessing.interfaces.ValidationTemplatePort;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * REST controller for managing validation templates.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST   /api/v1/validation-templates}          — create a new template</li>
 *   <li>{@code PUT    /api/v1/validation-templates/{flowType}} — update column rules</li>
 *   <li>{@code GET    /api/v1/validation-templates/{flowType}} — get active template</li>
 *   <li>{@code GET    /api/v1/validation-templates/{flowType}/history} — version history</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/validation-templates")
public class ValidationTemplateController {

    private static final Logger log = LoggerFactory.getLogger(ValidationTemplateController.class);

    private final ValidationTemplatePort templatePort;

    public ValidationTemplateController(ValidationTemplatePort templatePort) {
        this.templatePort = templatePort;
    }

    /**
     * Creates a new validation template for the given flow type.
     * Any existing active template for the same flow type is deactivated.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ValidationTemplateResponse> createTemplate(
            @Valid @RequestBody ValidationTemplateRequest request) {
        log.info("Creating validation template for flowType={}", request.flowType());
        return templatePort.saveTemplate(toDomain(request, null))
                .map(ValidationTemplateResponse::from);
    }

    /**
     * Updates the column rules for an existing flow type's template.
     * Increments the version; old template version is deactivated.
     */
    @PutMapping("/{flowType}")
    public Mono<ValidationTemplateResponse> updateTemplate(
            @PathVariable String flowType,
            @Valid @RequestBody ValidationTemplateRequest request) {
        log.info("Updating validation template for flowType={}", flowType);
        // Override flowType from path to avoid mismatch
        ValidationTemplateRequest merged = new ValidationTemplateRequest(
                flowType, request.columnRules());
        return templatePort.saveTemplate(toDomain(merged, null))
                .map(ValidationTemplateResponse::from);
    }

    /**
     * Returns the currently active template for the given flow type.
     * Returns {@code 404} if no active template exists.
     */
    @GetMapping("/{flowType}")
    public Mono<ResponseEntity<ValidationTemplateResponse>> getTemplate(
            @PathVariable String flowType) {
        return templatePort.getActiveTemplate(flowType)
                .map(t -> ResponseEntity.ok(ValidationTemplateResponse.from(t)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Returns all historical template versions for the given flow type, newest first.
     */
    @GetMapping("/{flowType}/history")
    public Flux<ValidationTemplateResponse> getTemplateHistory(@PathVariable String flowType) {
        return templatePort.listTemplates(flowType)
                .map(ValidationTemplateResponse::from);
    }

    // ---------- mapping ----------

    private ValidationTemplate toDomain(ValidationTemplateRequest req, String existingId) {
        List<ColumnRule> rules = req.columnRules().stream()
                .map(r -> new ColumnRule(
                        r.name(),
                        r.position(),
                        r.dataType() != null ? r.dataType() : ColumnDataType.STRING,
                        r.required(),
                        r.allowNull(),
                        r.maxLength(),
                        r.pattern()))
                .toList();
        return new ValidationTemplate(existingId, req.flowType(), 0, rules, true, null);
    }
}
