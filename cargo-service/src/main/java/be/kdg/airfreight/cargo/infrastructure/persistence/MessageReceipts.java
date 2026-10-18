package be.kdg.airfreight.cargo.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records when a message ID was first received, in its own transaction so the time survives a
 * failed attempt. Retries and requeues therefore resolve dates against the same instant. Plain SQL
 * is used for the insert-if-absent, which also holds when two deliveries arrive together.
 */
@Component
public class MessageReceipts {
    private final JdbcClient jdbc;

    MessageReceipts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Instant firstReceivedAt(String messageId, Instant now) {
        jdbc.sql(
                        "INSERT INTO message_receipts (message_id, first_received_at) VALUES (?, ?)"
                                + " ON CONFLICT (message_id) DO NOTHING")
                .params(messageId, Timestamp.from(now))
                .update();
        return jdbc.sql("SELECT first_received_at FROM message_receipts WHERE message_id = ?")
                .param(messageId)
                .query(Timestamp.class)
                .single()
                .toInstant();
    }
}
