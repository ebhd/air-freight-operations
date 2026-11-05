package be.kdg.airfreight.formatter.infrastructure.messaging.prepared;

import java.util.List;
import java.util.UUID;

public record PartnerMessagesPreparedMessage(
        int version, UUID sourceMessageId, List<PreparedContent> messages) {
    public static final int VERSION = 1;

    public record PreparedContent(String type, int formatVersion, String payload) {}
}
