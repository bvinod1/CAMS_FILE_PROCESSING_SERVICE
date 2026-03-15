package com.cams.fileprocessing.infrastructure.messaging;

import com.cams.fileprocessing.interfaces.MessagePublisherPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default (no-op) {@link MessagePublisherPort} implementation that logs events
 * as JSON instead of publishing to a real message queue.
 *
 * <p>This bean is active in all profiles until a profile-specific adapter is wired:
 * <ul>
 *   <li>{@code RabbitMqMessagePublisher} — Epic 1 T205 ({@code @Profile("local")})</li>
 *   <li>{@code PubSubMessagePublisher}   — Epic 1 T205 ({@code @Profile("gcp")})</li>
 * </ul>
 *
 * <p>Using {@link ConditionalOnMissingBean} ensures profile-specific adapters
 * automatically take precedence once they are added.
 */
@Component
@ConditionalOnMissingBean(
        value = MessagePublisherPort.class,
        ignored = LoggingMessagePublisher.class
)
public class LoggingMessagePublisher implements MessagePublisherPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingMessagePublisher.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * {@inheritDoc}
     *
     * <p>Serialises {@code event} to JSON and emits an INFO log entry.
     * No message is actually sent to a broker.
     */
    @Override
    public void publish(String topic, Object event) {
        try {
            String json = MAPPER.writeValueAsString(event);
            log.info("EVENT [{}] >>> {}", topic, json);
        } catch (JsonProcessingException e) {
            log.warn("EVENT [{}] >>> (serialisation failed: {}) payload={}",
                    topic, e.getMessage(), event);
        }
    }
}
