package be.kdg.airfreight.partner.infrastructure.persistence.jpa;

import be.kdg.airfreight.partner.domain.MessageContent;
import be.kdg.airfreight.partner.domain.MessageId;
import be.kdg.airfreight.partner.domain.PartnerMessage;
import be.kdg.airfreight.partner.domain.PublicationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "partner_messages",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source_message_id", "type"}))
public class JpaPartnerMessage {
    @Id private UUID id;

    @Column(name = "source_message_id", nullable = false)
    private UUID sourceMessageId;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUTC;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "format_version", nullable = false)
    private int formatVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicationState state;

    protected JpaPartnerMessage() {}

    public static JpaPartnerMessage fromDomain(PartnerMessage message) {
        var entity = new JpaPartnerMessage();
        entity.id = message.id().value();
        entity.sourceMessageId = message.sourceMessageId();
        entity.createdAtUTC = message.createdAtUTC();
        entity.type = message.content().type();
        entity.payload = message.content().payload();
        entity.formatVersion = message.content().formatVersion();
        entity.state = message.state();
        return entity;
    }

    public PartnerMessage toDomain() {
        return PartnerMessage.restore(
                new MessageId(id),
                sourceMessageId,
                createdAtUTC,
                new MessageContent(type, payload, formatVersion),
                state);
    }
}
