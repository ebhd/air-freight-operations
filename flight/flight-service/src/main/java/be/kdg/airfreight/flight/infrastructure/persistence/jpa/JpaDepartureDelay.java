package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import be.kdg.airfreight.flight.domain.flight.Delay;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "departure_delays")
public class JpaDepartureDelay {
    @Id private UUID id;

    @Column(nullable = false)
    private int minutes;

    @Column(name = "reason_code", nullable = false, length = 16)
    private String reasonCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "flight_number", referencedColumnName = "flight_number"),
        @JoinColumn(
                name = "scheduled_departure_date",
                referencedColumnName = "scheduled_departure_date"),
        @JoinColumn(
                name = "departure_airport_code",
                referencedColumnName = "departure_airport_code")
    })
    private JpaFlight flight;

    protected JpaDepartureDelay() {}

    private JpaDepartureDelay(
            UUID id, int minutes, String reasonCode, String description, JpaFlight flight) {
        this.id = id;
        this.minutes = minutes;
        this.reasonCode = reasonCode;
        this.description = description;
        this.flight = flight;
    }

    static JpaDepartureDelay fromDomain(Delay delay, JpaFlight flight) {
        return new JpaDepartureDelay(
                delay.id(), delay.minutes(), delay.reasonCode(), delay.description(), flight);
    }

    Delay toDomain() {
        return Delay.restore(id, minutes, reasonCode, description);
    }
}
