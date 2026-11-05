package be.kdg.airfreight.flight.infrastructure.messaging.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxMessage(
        UUID id,
        String exchange,
        String routingKey,
        String payload,
        Instant createdAt,
        Instant publishedAt) {

    public OutboxMessage publish(Instant publishedAt) {
        return new OutboxMessage(
                this.id,
                this.exchange,
                this.routingKey,
                this.payload,
                this.createdAt,
                Objects.requireNonNull(publishedAt, "publishedAt cannot be null"));
    }
}
