package com.cams.fileprocessing.features.validation;

import com.cams.fileprocessing.business.scan.ScanStatus;
import com.cams.fileprocessing.business.validation.ValidationService;
import com.cams.fileprocessing.features.scan.ScanCompletedEvent;
import com.cams.fileprocessing.features.validation.models.ValidationResult;
import com.cams.fileprocessing.infrastructure.local.jpa.ValidationErrorJpaEntity;
import com.cams.fileprocessing.infrastructure.local.jpa.ValidationErrorJpaRepo;
import com.cams.fileprocessing.infrastructure.local.jpa.ValidationResultJpaEntity;
import com.cams.fileprocessing.infrastructure.local.jpa.ValidationResultJpaRepo;
import com.cams.fileprocessing.interfaces.MessageConsumerPort;
import com.cams.fileprocessing.interfaces.MessagePublisherPort;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Event-driven worker that validates file headers after a clean scan.
 *
 * <p>Listens to {@code ScanCompletedEvent} messages. When result is {@link ScanStatus#CLEAN}:
 * <ol>
 *   <li>Calls {@link ValidationService} to validate the header row.</li>
 *   <li>Persists {@code validation_results} and {@code validation_errors} in a single transaction.</li>
 *   <li>Publishes {@link ValidationCompletedEvent} only after the DB commit (transactional outbox).</li>
 * </ol>
 *
 * <p>Messages with {@code result != CLEAN} are silently acknowledged (no validation needed).
 *
 * <p><strong>TODO (E6):</strong> Add FileRecord status transitions (VALIDATING / VALIDATED /
 * VALIDATION_FAILED) once {@code FileMetadataRepository} port is in place.
 */
@Component
@Profile("local")
public class ValidationWorker {

    private static final Logger log = LoggerFactory.getLogger(ValidationWorker.class);

    private static final String SCAN_COMPLETED_TOPIC = "cams.scan.completed";

    private final MessageConsumerPort<ScanCompletedEvent> messageConsumer;
    private final MessagePublisherPort messagePublisher;
    private final ValidationService validationService;
    private final ValidationResultJpaRepo validationResultRepo;
    private final ValidationErrorJpaRepo validationErrorRepo;

    public ValidationWorker(MessageConsumerPort<ScanCompletedEvent> messageConsumer,
                            MessagePublisherPort messagePublisher,
                            ValidationService validationService,
                            ValidationResultJpaRepo validationResultRepo,
                            ValidationErrorJpaRepo validationErrorRepo) {
        this.messageConsumer = messageConsumer;
        this.messagePublisher = messagePublisher;
        this.validationService = validationService;
        this.validationResultRepo = validationResultRepo;
        this.validationErrorRepo = validationErrorRepo;
    }

    @PostConstruct
    public void startConsuming() {
        messageConsumer
                .consume(SCAN_COMPLETED_TOPIC, ScanCompletedEvent.class)
                .filter(event -> event.result() == ScanStatus.CLEAN)
                .flatMap(this::handleScanCompleted)
                .doOnError(ex -> log.error("ValidationWorker encountered unrecoverable error", ex))
                .retry()
                .subscribe();
        log.info("ValidationWorker subscribed to topic '{}'", SCAN_COMPLETED_TOPIC);
    }

    private reactor.core.publisher.Mono<Void> handleScanCompleted(ScanCompletedEvent event) {
        MDC.put("fileId", event.fileId());
        MDC.put("flowType", event.flowType());
        log.info("Received ScanCompletedEvent — fileId={}, flowType={}", event.fileId(), event.flowType());

        return validationService
                .validate(event.fileId(), event.flowType(), event.bucket(), event.objectPath())
                .flatMap(result -> persistResultAndPublish(result, event))
                .doFinally(signal -> MDC.clear());
    }

    @Transactional
    public reactor.core.publisher.Mono<Void> persistResultAndPublish(
            ValidationResult result, ScanCompletedEvent event) {
        return reactor.core.publisher.Mono.fromRunnable(() -> {
            // 1. Persist validation_results row
            String resultId = UUID.randomUUID().toString();
            ValidationResultJpaEntity resultEntity = new ValidationResultJpaEntity();
            resultEntity.setResultId(resultId);
            resultEntity.setFileId(result.fileId());
            resultEntity.setFlowType(result.flowType());
            resultEntity.setTemplateId(""); // TODO: expose templateId from ValidationResult/Template
            resultEntity.setTemplateVersion(result.templateVersion());
            resultEntity.setStatus(result.status().name());
            resultEntity.setErrorCount(result.errors().size());
            resultEntity.setDurationMs(result.durationMs());
            resultEntity.setValidatedAt(result.validatedAt());
            validationResultRepo.save(resultEntity);

            // 2. Persist validation_errors rows (if any)
            result.errors().forEach(error -> {
                ValidationErrorJpaEntity errorEntity = new ValidationErrorJpaEntity();
                errorEntity.setErrorId(UUID.randomUUID().toString());
                errorEntity.setResultId(resultId);
                errorEntity.setColumnName(error.columnName());
                errorEntity.setPosition(error.position());
                errorEntity.setErrorCode(error.errorCode().name());
                errorEntity.setDetail(error.detail());
                validationErrorRepo.save(errorEntity);
            });

            log.info("Persisted validation result — fileId={}, status={}, errors={}",
                    result.fileId(), result.status(), result.errors().size());
        })
        // 3. Publish ValidationCompletedEvent after DB commit
        .then(reactor.core.publisher.Mono.fromRunnable(() -> {
            ValidationCompletedEvent outboxEvent = new ValidationCompletedEvent(
                    result.fileId(),
                    result.flowType(),
                    result.status(),
                    result.templateVersion(),
                    result.errors().size(),
                    result.validatedAt()
            );
            messagePublisher.publish("cams.validation.completed", outboxEvent);
            log.info("Published ValidationCompletedEvent — fileId={}, result={}",
                    result.fileId(), result.status());
        }));
    }
}
