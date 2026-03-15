package com.cams.fileprocessing.infrastructure.local;

import com.cams.fileprocessing.features.validation.models.*;
import com.cams.fileprocessing.infrastructure.local.jpa.ValidationColumnRuleJpaEntity;
import com.cams.fileprocessing.infrastructure.local.jpa.ValidationTemplateJpaEntity;
import com.cams.fileprocessing.infrastructure.local.jpa.ValidationTemplateJpaRepo;
import com.cams.fileprocessing.interfaces.ValidationTemplatePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Local (PostgreSQL-backed) implementation of {@link ValidationTemplatePort}.
 * Activated only when {@code spring.profiles.active=local}.
 */
@Component("rawValidationTemplatePort")
@Profile("local")
public class LocalValidationTemplateAdapter implements ValidationTemplatePort {

    private static final Logger log = LoggerFactory.getLogger(LocalValidationTemplateAdapter.class);

    private final ValidationTemplateJpaRepo repo;

    public LocalValidationTemplateAdapter(ValidationTemplateJpaRepo repo) {
        this.repo = repo;
    }

    @Override
    public Mono<ValidationTemplate> getActiveTemplate(String flowType) {
        return Mono.fromCallable(() -> repo.findByFlowTypeAndActiveTrue(flowType))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.map(entity -> Mono.just(toDomain(entity)))
                        .orElse(Mono.empty()))
                .doOnNext(t -> log.debug("Loaded active template for flowType={} version={}",
                        flowType, t.version()));
    }

    @Override
    public Mono<ValidationTemplate> saveTemplate(ValidationTemplate template) {
        return Mono.fromCallable(() -> {
            // Deactivate all previous active templates for this flowType
            repo.findByFlowTypeAndActiveTrue(template.flowType())
                    .ifPresent(existing -> {
                        existing.setActive(false);
                        repo.save(existing);
                    });

            int nextVersion = repo.findMaxVersionByFlowType(template.flowType()) + 1;
            String id = template.id() != null ? template.id() : UUID.randomUUID().toString();

            ValidationTemplateJpaEntity entity = new ValidationTemplateJpaEntity();
            entity.setTemplateId(id);
            entity.setFlowType(template.flowType());
            entity.setVersion(nextVersion);
            entity.setActive(true);
            entity.setCreatedAt(Instant.now());

            List<ValidationColumnRuleJpaEntity> rules = template.columnRules().stream()
                    .map(r -> toRuleEntity(r, entity))
                    .toList();
            entity.setColumnRules(rules);

            return repo.save(entity);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .map(this::toDomain)
        .doOnNext(t -> log.info("Saved template flowType={} version={} id={}",
                t.flowType(), t.version(), t.id()));
    }

    @Override
    public Flux<ValidationTemplate> listTemplates(String flowType) {
        return Mono.fromCallable(() -> repo.findByFlowTypeOrderByVersionDesc(flowType))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(this::toDomain);
    }

    @Override
    public Flux<String> listActiveFlowTypes() {
        return Mono.fromCallable(repo::findActiveFlowTypes)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    // ---------- mapping helpers ----------

    private ValidationTemplate toDomain(ValidationTemplateJpaEntity e) {
        List<ColumnRule> rules = e.getColumnRules().stream()
                .map(this::toColumnRule)
                .toList();
        return new ValidationTemplate(
                e.getTemplateId(),
                e.getFlowType(),
                e.getVersion(),
                rules,
                e.isActive(),
                e.getCreatedAt()
        );
    }

    private ColumnRule toColumnRule(ValidationColumnRuleJpaEntity e) {
        return new ColumnRule(
                e.getColumnName(),
                e.getPosition(),
                ColumnDataType.valueOf(e.getDataType()),
                e.isRequired(),
                e.isAllowNull(),
                e.getMaxLength(),
                e.getPattern()
        );
    }

    private ValidationColumnRuleJpaEntity toRuleEntity(ColumnRule rule,
                                                        ValidationTemplateJpaEntity parent) {
        ValidationColumnRuleJpaEntity e = new ValidationColumnRuleJpaEntity();
        e.setRuleId(UUID.randomUUID().toString());
        e.setTemplate(parent);
        e.setColumnName(rule.name());
        e.setPosition(rule.position());
        e.setDataType(rule.dataType().name());
        e.setRequired(rule.required());
        e.setAllowNull(rule.allowNull());
        e.setMaxLength(rule.maxLength());
        e.setPattern(rule.pattern());
        return e;
    }
}
