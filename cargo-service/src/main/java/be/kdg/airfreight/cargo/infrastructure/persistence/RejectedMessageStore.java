package be.kdg.airfreight.cargo.infrastructure.persistence;

import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaRejectedMessage;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaRejectedMessageRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps rejected incoming messages apart from the domain data, once per message ID. */
@Component
public class RejectedMessageStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(RejectedMessageStore.class);

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
            LOGGER.info("Rejected message {} was redelivered; already stored", messageId);
            return;
        }
        rejectedMessages.save(new JpaRejectedMessage(messageId, payload, reason, Instant.now()));
        LOGGER.warn("Rejected message {}: {}", messageId, reason);
    }
}
