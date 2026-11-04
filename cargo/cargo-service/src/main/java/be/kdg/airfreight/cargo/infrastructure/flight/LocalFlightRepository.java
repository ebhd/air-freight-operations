package be.kdg.airfreight.cargo.infrastructure.flight;

import be.kdg.airfreight.cargo.domain.flight.FlightCopyRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import java.time.Clock;
import java.time.Duration;
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
    // flight stamps an event before its commit so a change can be stamped earlier than our call
    // but still be missing from the answer, an older event applied later is harmless because
    // the newest one still wins
    static final Duration COMMIT_MARGIN = Duration.ofSeconds(5);

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
        // before the call and minus the margin so an event of a change not in the answer stays
        // newer
        Instant askedAt = clock.instant().minus(COMMIT_MARGIN);
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
