package be.kdg.airfreight.partner.domain;

public record MessageContent(String type, String payload, int formatVersion) {
    public MessageContent {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("message type must not be blank");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("message payload must not be blank");
        }
        if (formatVersion < 1) {
            throw new IllegalArgumentException("format version must be positive");
        }
    }
}
