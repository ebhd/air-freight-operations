package be.kdg.airfreight.cargo.infrastructure.persistence;

import be.kdg.airfreight.cargo.application.booking.ProcessedMessage;
import be.kdg.airfreight.cargo.application.booking.ProcessedMessageRepository;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaProcessedMessage;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaProcessedMessageRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class DbProcessedMessageRepository implements ProcessedMessageRepository {
    private final JpaProcessedMessageRepository messages;

    DbProcessedMessageRepository(JpaProcessedMessageRepository messages) {
        this.messages = messages;
    }

    @Override
    public Optional<ProcessedMessage> findByMessageId(String messageId) {
        return messages.findById(messageId).map(JpaProcessedMessage::toDomain);
    }

    @Override
    public void save(ProcessedMessage processedMessage) {
        messages.save(JpaProcessedMessage.fromDomain(processedMessage));
    }
}
