package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import be.kdg.airfreight.flight.domain.Flight;
import be.kdg.airfreight.flight.domain.FlightStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "flights")
public class JpaFlight {
    @EmbeddedId private JpaFlightId id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "arrival_airport_code", nullable = false, length = 3)
    private String arrivalAirportCode;

    @Column(name = "aircraft_registration", nullable = false, length = 32)
    private String aircraftRegistration;

    @Column(name = "scheduled_departure", nullable = false)
    private Instant scheduledDeparture;

    @Column(name = "scheduled_arrival", nullable = false)
    private Instant scheduledArrival;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FlightStatus status;

    protected JpaFlight() {}

    private JpaFlight(
            JpaFlightId id,
            String arrivalAirportCode,
            String aircraftRegistration,
            Instant scheduledDeparture,
            Instant scheduledArrival,
            FlightStatus status) {
        this.id = id;
        this.arrivalAirportCode = arrivalAirportCode;
        this.aircraftRegistration = aircraftRegistration;
        this.scheduledDeparture = scheduledDeparture;
        this.scheduledArrival = scheduledArrival;
        this.status = status;
    }

    public static JpaFlight fromDomain(Flight flight) {
        return new JpaFlight(
                JpaFlightId.fromDomain(flight.key()),
                flight.arrivalAirportCode(),
                flight.aircraftRegistration(),
                flight.scheduledDeparture(),
                flight.scheduledArrival(),
                flight.status());
    }

    public Flight toDomain() {
        return Flight.restore(
                id.toDomain(),
                arrivalAirportCode,
                aircraftRegistration,
                scheduledDeparture,
                scheduledArrival,
                status);
    }

    public JpaFlightId getId() {
        return id;
    }

    public String getArrivalAirportCode() {
        return arrivalAirportCode;
    }

    public String getAircraftRegistration() {
        return aircraftRegistration;
    }

    public Instant getScheduledDeparture() {
        return scheduledDeparture;
    }

    public Instant getScheduledArrival() {
        return scheduledArrival;
    }

    public FlightStatus getStatus() {
        return status;
    }
}
