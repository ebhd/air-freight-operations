package be.kdg.airfreight.cargo.infrastructure.flight;

import be.kdg.airfreight.cargo.domain.flight.FlightCopyRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

// cargos copy first so only a flight cargo has never heard of is asked at flight
@Repository
@Slf4j
class LocalFlightRepository implements FlightRepository {
    private final FlightCopyRepository copies;
    private final FlightOperationsClient flightOperations;
    private final Clock clock;

    LocalFlightRepository(
            FlightCopyRepository copies, FlightOperationsClient flightOperations, Clock clock) {
        this.copies = copies;
        this.flightOperations = flightOperations;
        this.clock = clock;
    }

    @Override
    public Optional<FlightInformation> findByFlightKey(FlightKey flightKey) {
        Optional<FlightInformation> copy = copies.findCopy(flightKey);
        if (copy.isPresent()) {
            return copy;
        }
        // the time before the call so an event flight sends during the call still counts as newer
        Instant askedAt = clock.instant();
        // a 404 is not stored because the flight can be created a moment later
        Optional<FlightInformation> fetched = flightOperations.fetch(flightKey);
        if (fetched.isPresent()) {
            copies.saveIfNewer(fetched.get(), askedAt);
            log.info("Fetched flight {} from Flight Operations into the copy", flightKey);
        }
        return fetched;
    }

    @Override
    public List<FlightInformation> findByScheduledDepartureDate(LocalDate scheduledDepartureDate) {
        return copies.findCopiesOn(scheduledDepartureDate);
    }
}
