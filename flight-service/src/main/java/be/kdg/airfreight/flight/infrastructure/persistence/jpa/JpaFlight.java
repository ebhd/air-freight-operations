package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
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

    @OneToMany(
            mappedBy = "flight",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<JpaDelay> delays = new ArrayList<>();

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
        JpaFlight jpaFlight =
                new JpaFlight(
                        JpaFlightId.fromDomain(flight.key()),
                        flight.arrivalAirportCode(),
                        flight.aircraftRegistration(),
                        flight.scheduledDeparture(),
                        flight.scheduledArrival(),
                        flight.status());
        jpaFlight.delays =
                flight.delays().stream()
                        .map(delay -> JpaDelay.fromDomain(delay, jpaFlight))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return jpaFlight;
    }

    public Flight toDomain() {
        return Flight.restore(
                id.toDomain(),
                arrivalAirportCode,
                aircraftRegistration,
                scheduledDeparture,
                scheduledArrival,
                status,
                delays.stream().map(JpaDelay::toDomain).toList());
    }
}
