package be.kdg.airfreight.flight.infrastructure.persistence;

import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import be.kdg.airfreight.flight.domain.flight.FlightStatus;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlight;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlightId;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaFlightRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    public Optional<Flight> findByIdForUpdate(FlightKey key) {
        return flights.findByIdForUpdate(JpaFlightId.fromDomain(key)).map(JpaFlight::toDomain);
    }

    @Override
    public List<Flight> findByScheduledDepartureDate(LocalDate date) {
        return flights.findByIdScheduledDepartureDateOrderByScheduledDepartureAsc(date).stream()
                .map(JpaFlight::toDomain)
                .toList();
    }

    @Override
    public List<Flight> findScheduledBefore(Instant end) {
        return flights.findByScheduledDepartureBeforeOrderByScheduledDepartureAsc(end).stream()
                .map(JpaFlight::toDomain)
                .toList();
    }

    @Override
    public Flight save(Flight flight) {
        return flights.save(JpaFlight.fromDomain(flight)).toDomain();
    }

    // two old flights can expect the same new key, the first replan wins and the other waits
    @Override
    public Optional<Flight> findReplacedBy(FlightKey newKey, Instant newScheduledDeparture) {
        return flights
                .findReplacedBy(
                        FlightStatus.REPLANNED,
                        newKey.flightNumber(),
                        newKey.departureAirportCode(),
                        newScheduledDeparture)
                .stream()
                .findFirst()
                .map(JpaFlight::toDomain);
    }
}
