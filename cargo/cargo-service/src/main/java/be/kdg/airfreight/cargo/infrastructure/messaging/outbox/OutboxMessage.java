package be.kdg.airfreight.cargo.infrastructure.messaging.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// one event waiting for rabbitmq, the id is also the amqp message id on every attempt
public record OutboxMessage(
        UUID id,
        String exchange,
        String routingKey,
        String payload,
        Instant createdAt,
        Instant publishedAt) {

    public OutboxMessage publish(Instant publishedAt) {
        return new OutboxMessage(
                id,
                exchange,
                routingKey,
                payload,
                createdAt,
                Objects.requireNonNull(publishedAt, "publishedAt cannot be null"));
    }
}
