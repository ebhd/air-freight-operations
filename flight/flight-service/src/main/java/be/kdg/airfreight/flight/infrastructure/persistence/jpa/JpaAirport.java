package be.kdg.airfreight.flight.infrastructure.persistence.jpa;

import be.kdg.airfreight.flight.domain.airport.Airport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "airports")
public class JpaAirport {
    @Id
    @Column(length = 3)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    protected JpaAirport() {}

    public Airport toDomain() {
        return new Airport(code, name);
    }
}
