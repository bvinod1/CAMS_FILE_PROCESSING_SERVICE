package com.cams.fileprocessing.infrastructure.messaging;

import com.cams.fileprocessing.interfaces.MessageConsumerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * No-op {@link MessageConsumerPort} used when no real messaging infrastructure is configured.
 *
 * <p>Activated only when no other {@link MessageConsumerPort} bean is present in the context
 * (i.e., when neither the {@code local} nor {@code gcp} profile is active).
 *
 * <p>Returns an empty {@link Flux} — no messages are ever emitted.
 * This prevents workers from failing to start in CI or local unit-test runs.
 */
@Component
@ConditionalOnMissingBean(value = MessageConsumerPort.class,
        ignored = LoggingMessageConsumer.class)
public class LoggingMessageConsumer<T> implements MessageConsumerPort<T> {

    private static final Logger log = LoggerFactory.getLogger(LoggingMessageConsumer.class);

    @Override
    public Flux<T> consume(String topic, Class<T> type) {
        log.warn("LoggingMessageConsumer: no real MessageConsumerPort configured. " +
                "Topic '{}' will receive no messages. " +
                "Activate 'local' or 'gcp' profile to enable real message consumption.", topic);
        return Flux.empty();
    }
}
