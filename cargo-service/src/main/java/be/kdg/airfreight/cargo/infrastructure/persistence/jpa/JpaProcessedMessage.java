package be.kdg.airfreight.cargo.infrastructure.persistence.jpa;

import be.kdg.airfreight.cargo.application.BookUldResult;
import be.kdg.airfreight.cargo.application.ProcessedMessage;
import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.UldCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "processed_messages")
public class JpaProcessedMessage {
    @Id
    @Column(name = "message_id", nullable = false, length = 255)
    private String messageId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "flight_number", nullable = false, length = 32)
    private String flightNumber;

    @Column(name = "scheduled_departure_date", nullable = false)
    private LocalDate scheduledDepartureDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "departure_airport_code", nullable = false, length = 3)
    private String departureAirportCode;

    @Column(name = "uld_code", nullable = false, length = 32)
    private String uldCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private BookUldResult.Outcome outcome;

    protected JpaProcessedMessage() {}

    public static JpaProcessedMessage fromDomain(ProcessedMessage message) {
        JpaProcessedMessage entity = new JpaProcessedMessage();
        entity.messageId = message.messageId();
        entity.processedAt = message.processedAt();
        entity.flightNumber = message.flightKey().flightNumber();
        entity.scheduledDepartureDate = message.flightKey().scheduledDepartureDate();
        entity.departureAirportCode = message.flightKey().departureAirportCode().value();
        entity.uldCode = message.uldCode().value();
        entity.outcome = message.outcome();
        return entity;
    }

    public ProcessedMessage toDomain() {
        return new ProcessedMessage(
                messageId,
                processedAt,
                new FlightKey(
                        flightNumber,
                        scheduledDepartureDate,
                        new AirportCode(departureAirportCode)),
                new UldCode(uldCode),
                outcome);
    }
}
