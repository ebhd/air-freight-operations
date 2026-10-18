package be.kdg.airfreight.cargo.application;

import java.util.Optional;

/** Remembers processed ULD messages so a redelivery has no second effect. */
public interface ProcessedMessageRepository {
    Optional<ProcessedMessage> findByMessageId(String messageId);

    void save(ProcessedMessage processedMessage);
}
