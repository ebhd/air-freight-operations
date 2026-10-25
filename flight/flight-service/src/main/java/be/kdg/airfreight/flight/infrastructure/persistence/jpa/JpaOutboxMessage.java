package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import be.kdg.airfreight.flight.infrastructure.messaging.outbox.OutboxMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "outbox_messages")
public class JpaOutboxMessage {
    @Id private UUID id;

    @Column(name = "exchange_name", nullable = false)
    private String exchange;

    private String routingKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    private Instant createdAt;
    private Instant publishedAt;

    protected JpaOutboxMessage() {}

    private JpaOutboxMessage(
            UUID id,
            String exchange,
            String routingKey,
            String payload,
            Instant createdAt,
            Instant publishedAt) {
        this.id = id;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.payload = payload;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
    }

    public static JpaOutboxMessage fromDomain(OutboxMessage message) {
        return new JpaOutboxMessage(
                message.id(),
                message.exchange(),
                message.routingKey(),
                message.payload(),
                message.createdAt(),
                message.publishedAt());
    }

    public OutboxMessage toDomain() {
        return new OutboxMessage(id, exchange, routingKey, payload, createdAt, publishedAt);
    }
}
