package com.cams.fileprocessing.infrastructure.local;

import com.cams.fileprocessing.features.validation.models.*;
import com.cams.fileprocessing.infrastructure.local.jpa.ValidationErrorJpaEntity;
import com.cams.fileprocessing.infrastructure.local.jpa.ValidationErrorJpaRepo;
import com.cams.fileprocessing.infrastructure.local.jpa.ValidationResultJpaEntity;
import com.cams.fileprocessing.infrastructure.local.jpa.ValidationResultJpaRepo;
import com.cams.fileprocessing.interfaces.ValidationResultPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Local (PostgreSQL-backed) implementation of {@link ValidationResultPort}.
 * Activated only when {@code spring.profiles.active=local}.
 */
@Component
@Profile("local")
public class LocalValidationResultAdapter implements ValidationResultPort {

    private final ValidationResultJpaRepo resultRepo;
    private final ValidationErrorJpaRepo errorRepo;

    public LocalValidationResultAdapter(ValidationResultJpaRepo resultRepo,
                                        ValidationErrorJpaRepo errorRepo) {
        this.resultRepo = resultRepo;
        this.errorRepo = errorRepo;
    }

    @Override
    public Mono<ValidationResult> findLatestByFileId(String fileId) {
        return Mono.fromCallable(() -> resultRepo.findByFileId(fileId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(results -> results.stream()
                        .findFirst()
                        .map(entity -> Mono.just(toDomain(entity, fileId)))
                        .orElse(Mono.empty()));
    }

    @Override
    public Flux<ValidationError> findErrorsByFileId(String fileId) {
        return findLatestByFileId(fileId)
                .flatMapMany(result -> {
                    // Find the resultId for this file's latest result
                    return Mono.fromCallable(() -> resultRepo.findByFileId(fileId))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(results -> results.stream()
                                    .findFirst()
                                    .map(r -> Flux.fromIterable(errorRepo.findByResultId(r.getResultId()))
                                            .map(this::toErrorDomain))
                                    .orElse(Flux.empty()));
                });
    }

    // ---------- mapping ----------

    private ValidationResult toDomain(ValidationResultJpaEntity e, String fileId) {
        List<ValidationError> errors = errorRepo.findByResultId(e.getResultId())
                .stream().map(this::toErrorDomain).toList();
        return new ValidationResult(
                e.getFileId(),
                e.getFlowType(),
                e.getTemplateVersion(),
                ValidationResultStatus.valueOf(e.getStatus()),
                errors,
                e.getDurationMs() != null ? e.getDurationMs() : 0L,
                e.getValidatedAt()
        );
    }

    private ValidationError toErrorDomain(ValidationErrorJpaEntity e) {
        return new ValidationError(
                e.getColumnName(),
                e.getPosition(),
                ValidationErrorCode.valueOf(e.getErrorCode()),
                e.getDetail()
        );
    }
}
