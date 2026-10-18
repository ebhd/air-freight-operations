package be.kdg.airfreight.partner.domain;

import java.util.Objects;
import java.util.UUID;

public record MessageId(UUID value) {
    public MessageId {
        Objects.requireNonNull(value, "message ID must not be null");
    }

    public static MessageId generate() {
        return new MessageId(UUID.randomUUID());
    }
}
