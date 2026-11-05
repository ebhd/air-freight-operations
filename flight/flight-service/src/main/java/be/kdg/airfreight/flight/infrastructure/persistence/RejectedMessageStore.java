package be.kdg.airfreight.flight.infrastructure.persistence;

import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaRejectedMessage;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaRejectedMessageRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
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
    public void store(String messageId, byte[] body, String reason) {
        String id = withoutNul(messageId);
        if (id != null && rejectedMessages.existsByMessageId(id)) {
            log.info("Rejected message {} was redelivered; already stored", id);
            return;
        }
        String payload = payloadText(body);
        String cleanReason = withoutNul(reason);
        rejectedMessages.save(new JpaRejectedMessage(id, payload, cleanReason, clock.instant()));
        log.warn("Rejected message {}: {}. Content: {}", id, cleanReason, payload);
    }

    // a body that is no clean text would be changed or refused by the text column, base64 keeps
    // every byte
    private static String payloadText(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8);
        boolean sameBytes = Arrays.equals(text.getBytes(StandardCharsets.UTF_8), body);
        if (sameBytes && text.indexOf('\0') < 0) {
            return text;
        }
        return "base64:" + Base64.getEncoder().encodeToString(body);
    }

    // postgres refuses a NUL character in text, the reason can quote one from the message
    private static String withoutNul(String text) {
        return text == null ? null : text.replace("\0", "");
    }
}
