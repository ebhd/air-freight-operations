package be.kdg.airfreight.cargo.application.flight;

import be.kdg.airfreight.cargo.domain.flight.FlightCopyRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// keeps cargo's flight copy up to date with flight's events
// no transaction here because each save is one atomic statement that refuses an older change
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
            log.info("Ignored FlightCreated for {}: the copy is already newer", flight.flightKey());
        }
    }

    public void recordEstimates(
            FlightKey flightKey,
            Instant estimatedDeparture,
            Instant estimatedArrival,
            Instant occurredOn) {
        // a flight the copy does not have yet is fetched and stored by this lookup
        if (flights.findByFlightKey(flightKey).isEmpty()) {
            log.warn(
                    "Flight Operations does not know flight {} from its own estimate event",
                    flightKey);
            return;
        }
        if (copies.saveEstimatesIfNewer(
                flightKey, estimatedDeparture, estimatedArrival, occurredOn)) {
            log.info(
                    "Updated estimates of flight {} to ETD {} and ETA {}",
                    flightKey,
                    estimatedDeparture,
                    estimatedArrival);
        } else {
            log.info("Ignored older estimates for flight {}", flightKey);
        }
    }

    public void recordDeparted(FlightKey flightKey) {
        advance(flightKey, FlightStatus.DEPARTED);
    }

    public void recordLanded(FlightKey flightKey) {
        advance(flightKey, FlightStatus.LANDED);
    }

    // no time check: the status only moves forward, so a redelivery or an older event changes
    // nothing and landed before departed ends in landed
    private void advance(FlightKey flightKey, FlightStatus status) {
        // a flight the copy does not have yet is fetched and stored by this lookup
        if (flights.findByFlightKey(flightKey).isEmpty()) {
            log.warn(
                    "Flight Operations does not know flight {} from its own status event",
                    flightKey);
            return;
        }
        if (copies.advanceStatus(flightKey, status)) {
            log.info("Flight {} is now {} in the copy", flightKey, status);
        } else {
            log.info("Flight {} was already {} or further in the copy", flightKey, status);
        }
    }

    // the old flight of a replanning is final, so its copy only needs the status
    public void recordReplanned(FlightKey flightKey) {
        advance(flightKey, FlightStatus.REPLANNED);
    }
}
