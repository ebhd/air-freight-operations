package be.kdg.airfreight.cargo.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MessageReceipts {
    private final JdbcClient jdbc;

    MessageReceipts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // own transaction so the first receipt time survives a failed attempt
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
