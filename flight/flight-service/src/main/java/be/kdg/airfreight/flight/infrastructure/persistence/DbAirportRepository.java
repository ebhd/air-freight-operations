package be.kdg.airfreight.flight.infrastructure.persistence;

import be.kdg.airfreight.flight.domain.airport.Airport;
import be.kdg.airfreight.flight.domain.airport.AirportRepository;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaAirport;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaAirportRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class DbAirportRepository implements AirportRepository {
    private final JpaAirportRepository airports;

    DbAirportRepository(JpaAirportRepository airports) {
        this.airports = airports;
    }

    @Override
    public List<Airport> findAll() {
        return airports.findAllByOrderByCodeAsc().stream().map(JpaAirport::toDomain).toList();
    }
}
