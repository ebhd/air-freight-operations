package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import be.kdg.airfreight.flight.domain.FlightKey;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Embeddable
public class JpaFlightId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "flight_number", nullable = false, length = 32)
    private String flightNumber;

    @Column(name = "scheduled_departure_date", nullable = false)
    private LocalDate scheduledDepartureDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "departure_airport_code", nullable = false, length = 3)
    private String departureAirportCode;

    protected JpaFlightId() {}

    private JpaFlightId(
            String flightNumber, LocalDate scheduledDepartureDate, String departureAirportCode) {
        this.flightNumber = flightNumber;
        this.scheduledDepartureDate = scheduledDepartureDate;
        this.departureAirportCode = departureAirportCode;
    }

    public static JpaFlightId fromDomain(FlightKey key) {
        return new JpaFlightId(
                key.flightNumber(), key.scheduledDepartureDate(), key.departureAirportCode());
    }

    public FlightKey toDomain() {
        return new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof JpaFlightId id
                        && Objects.equals(flightNumber, id.flightNumber)
                        && Objects.equals(scheduledDepartureDate, id.scheduledDepartureDate)
                        && Objects.equals(departureAirportCode, id.departureAirportCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flightNumber, scheduledDepartureDate, departureAirportCode);
    }
}
