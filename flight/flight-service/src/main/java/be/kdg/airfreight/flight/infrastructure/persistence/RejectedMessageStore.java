package be.kdg.airfreight.flight.infrastructure.persistence;

import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaRejectedMessage;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaRejectedMessageRepository;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class RejectedMessageStore {
    private final JpaRejectedMessageRepository rejectedMessages;
    private final Clock clock;

    RejectedMessageStore(JpaRejectedMessageRepository rejectedMessages, Clock clock) {
        this.rejectedMessages = rejectedMessages;
        this.clock = clock;
    }

    // a redelivered message keeps its first row
    @Transactional
    public void store(String messageId, String payload, String reason) {
        if (messageId != null && rejectedMessages.existsByMessageId(messageId)) {
            log.info("Rejected message {} was redelivered; already stored", messageId);
            return;
        }
        rejectedMessages.save(new JpaRejectedMessage(messageId, payload, reason, clock.instant()));
        log.warn("Rejected message {}: {}", messageId, reason);
    }
}
