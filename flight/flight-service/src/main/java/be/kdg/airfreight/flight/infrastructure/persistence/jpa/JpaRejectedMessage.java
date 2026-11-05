package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "rejected_messages")
public class JpaRejectedMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", columnDefinition = "TEXT")
    private String messageId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "rejected_at", nullable = false)
    private Instant rejectedAt;

    protected JpaRejectedMessage() {}

    public JpaRejectedMessage(String messageId, String payload, String reason, Instant rejectedAt) {
        this.messageId = messageId;
        this.payload = payload;
        this.reason = reason;
        this.rejectedAt = rejectedAt;
    }
}
