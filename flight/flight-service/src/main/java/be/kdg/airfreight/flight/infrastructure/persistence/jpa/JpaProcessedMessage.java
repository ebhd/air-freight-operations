package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "processed_messages")
public class JpaProcessedMessage {
    @Id
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "flight_number", nullable = false, length = 32)
    private String flightNumber;

    @Column(name = "scheduled_departure_date", nullable = false)
    private LocalDate scheduledDepartureDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "departure_airport_code", nullable = false, length = 3)
    private String departureAirportCode;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected JpaProcessedMessage() {}

    public JpaProcessedMessage(UUID messageId, FlightKey flightKey, Instant processedAt) {
        this.messageId = messageId;
        this.flightNumber = flightKey.flightNumber();
        this.scheduledDepartureDate = flightKey.scheduledDepartureDate();
        this.departureAirportCode = flightKey.departureAirportCode();
        this.processedAt = processedAt;
    }
}
