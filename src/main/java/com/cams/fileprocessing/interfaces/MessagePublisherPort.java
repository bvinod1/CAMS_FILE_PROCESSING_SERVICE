package com.cams.fileprocessing.interfaces;

/**
 * Port interface for publishing domain events to a message queue.
 *
 * <p>All event publishing in business and feature classes must go through this
 * interface. Direct use of RabbitMQ, GCP Pub/Sub, or AWS SQS client libraries
 * in business classes is forbidden (enforced by ArchUnit).
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@code LoggingMessagePublisher} — logs the event as JSON; used as
 *       the default until profile-specific adapters are wired</li>
 *   <li>{@code RabbitMqMessagePublisher} — Spring AMQP; {@code @Profile("local")} (Epic 1 T205)</li>
 *   <li>{@code PubSubMessagePublisher} — Spring Cloud GCP Pub/Sub; {@code @Profile("gcp")} (Epic 1 T205)</li>
 * </ul>
 */
public interface MessagePublisherPort {

    /**
     * Publishes {@code event} to the specified topic/exchange/queue.
     *
     * <p>Implementations must be idempotent with respect to duplicate deliveries.
     * The caller's transaction should commit the database state change before
     * calling this method (transactional outbox pattern).
     *
     * @param topic the logical topic name (e.g. {@code "cams.file.received"});
     *              adapters map this to the platform-specific resource name
     * @param event the domain event to publish; will be serialised to JSON
     */
    void publish(String topic, Object event);
}
