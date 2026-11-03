package be.kdg.airfreight.cargo.application.flight;

import be.kdg.airfreight.cargo.domain.flight.FlightCopyRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// keeps cargos flight copy up to date with flights events
// no transaction here because saveIfNewer is one atomic statement that refuses an older change
@Service
@Slf4j
public class FlightCopyService {
    private final FlightCopyRepository copies;
    private final FlightRepository flights;

    public FlightCopyService(FlightCopyRepository copies, FlightRepository flights) {
        this.copies = copies;
        this.flights = flights;
    }

    public void recordCreated(FlightInformation flight, Instant occurredOn) {
        if (copies.saveIfNewer(flight, occurredOn)) {
            log.info("Added flight {} to the copy", flight.flightKey());
        } else {
            log.debug(
                    "Ignored FlightCreated for {}: the copy is already newer", flight.flightKey());
        }
    }

    public void recordEstimates(
            FlightKey flightKey,
            Instant estimatedDeparture,
            Instant estimatedArrival,
            Instant occurredOn) {
        Optional<FlightInformation> copy = copies.findCopy(flightKey);
        if (copy.isEmpty()) {
            // FlightCreated not seen yet so take the current state from flight once
            if (flights.findByFlightKey(flightKey).isEmpty()) {
                log.warn(
                        "Flight Operations does not know flight {} from its own estimate event",
                        flightKey);
            }
            return;
        }
        FlightInformation updated = copy.get().withEstimates(estimatedDeparture, estimatedArrival);
        if (copies.saveIfNewer(updated, occurredOn)) {
            log.info(
                    "Updated estimates of flight {} to ETD {} and ETA {}",
                    flightKey,
                    estimatedDeparture,
                    estimatedArrival);
        } else {
            log.debug("Ignored older estimates for flight {}", flightKey);
        }
    }
}
