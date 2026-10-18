package be.kdg.airfreight.partner.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PartnerMessage {
    private final MessageId id;
    private final UUID sourceMessageId;
    private final Instant createdAtUTC;
    private final MessageContent content;
    private PublicationState state;

    private PartnerMessage(
            MessageId id,
            UUID sourceMessageId,
            Instant createdAtUTC,
            MessageContent content,
            PublicationState state) {
        this.id = Objects.requireNonNull(id, "message ID must not be null");
        this.sourceMessageId =
                Objects.requireNonNull(sourceMessageId, "source message ID must not be null");
        this.createdAtUTC = Objects.requireNonNull(createdAtUTC, "creation time must not be null");
        this.content = Objects.requireNonNull(content, "message content must not be null");
        this.state = Objects.requireNonNull(state, "publication state must not be null");
    }

    public static PartnerMessage create(
            UUID sourceMessageId, Instant createdAtUTC, MessageContent content) {
        return new PartnerMessage(
                MessageId.generate(),
                sourceMessageId,
                createdAtUTC,
                content,
                PublicationState.PENDING);
    }

    public static PartnerMessage restore(
            MessageId id,
            UUID sourceMessageId,
            Instant createdAtUTC,
            MessageContent content,
            PublicationState state) {
        return new PartnerMessage(id, sourceMessageId, createdAtUTC, content, state);
    }

    public void markPublished() {
        state = PublicationState.PUBLISHED;
    }

    public MessageId id() {
        return id;
    }

    public UUID sourceMessageId() {
        return sourceMessageId;
    }

    public Instant createdAtUTC() {
        return createdAtUTC;
    }

    public MessageContent content() {
        return content;
    }

    public PublicationState state() {
        return state;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PartnerMessage message && id.equals(message.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
