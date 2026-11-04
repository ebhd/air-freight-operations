package be.kdg.airfreight.cargo.application.flight;

import be.kdg.airfreight.cargo.domain.flight.FlightCopyRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// keeps cargos flight copy up to date with flights events
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
            log.debug(
                    "Ignored FlightCreated for {}: the copy is already newer", flight.flightKey());
        }
    }

    public void recordEstimates(
            FlightKey flightKey,
            Instant estimatedDeparture,
            Instant estimatedArrival,
            Instant occurredOn) {
        if (copies.findCopy(flightKey).isEmpty()) {
            // FlightCreated not seen yet so take the current state from flight once, the lookup
            // stores it in the copy
            if (flights.findByFlightKey(flightKey).isEmpty()) {
                log.warn(
                        "Flight Operations does not know flight {} from its own estimate event",
                        flightKey);
                return;
            }
        }
        // also after the lookup: the created listener runs on its own thread and can store the
        // schedule as estimates between the two reads, then only this event brings the delay
        if (copies.saveEstimatesIfNewer(
                flightKey, estimatedDeparture, estimatedArrival, occurredOn)) {
            log.info(
                    "Updated estimates of flight {} to ETD {} and ETA {}",
                    flightKey,
                    estimatedDeparture,
                    estimatedArrival);
        } else {
            log.debug("Ignored older estimates for flight {}", flightKey);
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
        if (copies.findCopy(flightKey).isEmpty()) {
            // the lookup stores the flight with its current status, which is already the new one
            if (flights.findByFlightKey(flightKey).isEmpty()) {
                log.warn(
                        "Flight Operations does not know flight {} from its own status event",
                        flightKey);
                return;
            }
        }
        if (copies.advanceStatus(flightKey, status)) {
            log.info("Flight {} is now {} in the copy", flightKey, status);
        } else {
            log.debug("Flight {} was already {} or further in the copy", flightKey, status);
        }
    }

    // the old flight of a replanning is final, so its copy only needs the status
    public void recordReplanned(FlightKey flightKey, Instant occurredOn) {
        if (copies.findCopy(flightKey).isEmpty()) {
            // not seen yet so take the current state from flight once, which is already replanned
            if (flights.findByFlightKey(flightKey).isEmpty()) {
                log.warn(
                        "Flight Operations does not know flight {} from its own replanned event",
                        flightKey);
                return;
            }
        }
        if (copies.saveStatusIfNewer(flightKey, FlightStatus.REPLANNED, occurredOn)) {
            log.info("Marked flight {} as replanned in the copy", flightKey);
        } else {
            log.debug("Flight {} was already replanned in the copy", flightKey);
        }
    }
}
