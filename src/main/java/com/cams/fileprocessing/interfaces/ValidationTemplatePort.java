package com.cams.fileprocessing.interfaces;

import com.cams.fileprocessing.features.validation.models.ValidationTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Port interface for reading and managing validation templates.
 *
 * <p>Local adapter: {@code JpaValidationTemplateRepository} backed by PostgreSQL.
 * <p>GCP adapter: {@code SpannerValidationTemplateRepository} (Epic 6).
 *
 * <p>All business logic must inject this interface — never a JPA repository directly
 * (ArchUnit-enforced).
 */
public interface ValidationTemplatePort {

    /**
     * Returns the currently active template for the given flow type.
     *
     * @param flowType e.g. {@code "NAV"}, {@code "TRANSACTION"}
     * @return a {@link Mono} emitting the active template, or empty if none exists
     */
    Mono<ValidationTemplate> getActiveTemplate(String flowType);

    /**
     * Saves (creates or updates) a template. Increments version automatically.
     *
     * @param template the template to persist (id may be null for new templates)
     * @return a {@link Mono} emitting the persisted template with assigned id and version
     */
    Mono<ValidationTemplate> saveTemplate(ValidationTemplate template);

    /**
     * Returns all versions of templates for the given flow type, ordered by version descending.
     *
     * @param flowType the flow type to query
     * @return a {@link Flux} of all template versions
     */
    Flux<ValidationTemplate> listTemplates(String flowType);

    /**
     * Returns all flow types that have at least one active template.
     *
     * @return a {@link Flux} of distinct flow type strings
     */
    Flux<String> listActiveFlowTypes();
}
