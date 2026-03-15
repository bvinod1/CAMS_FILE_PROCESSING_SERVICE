package com.cams.fileprocessing.interfaces;

import reactor.core.publisher.Flux;

/**
 * Port interface for consuming messages from a message broker.
 *
 * <p>Local adapter: RabbitMQ via Spring AMQP.
 * <p>GCP adapter: GCP Pub/Sub via Spring Cloud GCP.
 *
 * <p>Implementations must be activated by {@code @Profile("local")} or {@code @Profile("gcp")}.
 * Workers must inject this interface — never Spring AMQP or Pub/Sub types directly.
 *
 * @param <T> the deserialized message payload type
 */
public interface MessageConsumerPort<T> {

    /**
     * Returns a cold, infinite {@link Flux} of messages from the given topic/queue.
     *
     * <p>Messages are acknowledged only after the subscriber completes processing without error.
     * On error the message is nack'd and re-queued (or sent to DLQ per broker policy).
     *
     * @param topic  the logical topic name (mapped to a queue or subscription by the adapter)
     * @param type   the class to deserialize JSON payloads into
     * @return a {@link Flux} that emits received messages
     */
    Flux<T> consume(String topic, Class<T> type);
}
