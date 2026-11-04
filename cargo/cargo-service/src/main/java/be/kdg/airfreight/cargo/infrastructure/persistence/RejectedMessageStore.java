package be.kdg.airfreight.cargo.infrastructure.persistence;

import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaRejectedMessage;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaRejectedMessageRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class RejectedMessageStore {
    private final JpaRejectedMessageRepository rejectedMessages;

    RejectedMessageStore(JpaRejectedMessageRepository rejectedMessages) {
        this.rejectedMessages = rejectedMessages;
    }

    @Transactional(readOnly = true)
    public boolean contains(String messageId) {
        return rejectedMessages.existsByMessageId(messageId);
    }

    @Transactional
    public void store(String messageId, String payload, String reason) {
        if (messageId != null && rejectedMessages.existsByMessageId(messageId)) {
            log.info("Rejected message {} was redelivered; already stored", messageId);
            return;
        }
        rejectedMessages.save(new JpaRejectedMessage(messageId, payload, reason, Instant.now()));
        log.warn("Rejected message {}: {}", messageId, reason);
    }
}
