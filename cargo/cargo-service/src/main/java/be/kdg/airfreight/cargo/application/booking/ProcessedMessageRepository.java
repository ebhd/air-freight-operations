package be.kdg.airfreight.cargo.application.booking;

import java.util.Optional;

public interface ProcessedMessageRepository {
    Optional<ProcessedMessage> findByMessageId(String messageId);

    void save(ProcessedMessage processedMessage);
}
