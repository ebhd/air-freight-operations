package be.kdg.airfreight.flight.infrastructure.persistence;

import be.kdg.airfreight.flight.domain.Flight;
import be.kdg.airfreight.flight.domain.FlightKey;
import be.kdg.airfreight.flight.domain.FlightRepository;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlight;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlightId;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlightRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class DbFlightRepository implements FlightRepository {
    private final JpaFlightRepository flights;

    DbFlightRepository(JpaFlightRepository flights) {
        this.flights = flights;
    }

    @Override
    public Optional<Flight> findById(FlightKey key) {
        return flights.findById(JpaFlightId.fromDomain(key)).map(JpaFlight::toDomain);
    }

    @Override
    public Flight save(Flight flight) {
        return flights.save(JpaFlight.fromDomain(flight)).toDomain();
    }
}
