package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import be.kdg.airfreight.cargo.infrastructure.messaging.outbox.OutboxMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
public class JpaOutboxMessage {
    @Id private UUID id;

    @Column(name = "exchange_name", nullable = false)
    private String exchange;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // null while rabbitmq has not confirmed the message
    @Column(name = "published_at")
    private Instant publishedAt;

    protected JpaOutboxMessage() {}

    public static JpaOutboxMessage fromDomain(OutboxMessage message) {
        JpaOutboxMessage entity = new JpaOutboxMessage();
        entity.id = message.id();
        entity.exchange = message.exchange();
        entity.routingKey = message.routingKey();
        entity.payload = message.payload();
        entity.createdAt = message.createdAt();
        entity.publishedAt = message.publishedAt();
        return entity;
    }

    public OutboxMessage toDomain() {
        return new OutboxMessage(id, exchange, routingKey, payload, createdAt, publishedAt);
    }
}
