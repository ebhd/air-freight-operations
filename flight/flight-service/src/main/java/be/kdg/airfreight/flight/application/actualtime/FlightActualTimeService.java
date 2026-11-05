package be.kdg.airfreight.flight.application.actualtime;

import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.events.FlightDeparted;
import be.kdg.airfreight.flight.domain.events.FlightLanded;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import be.kdg.airfreight.flight.domain.flight.actualtime.ActualTimePolicy;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// registers atd and ata, its own service because it is about the lifecycle and not the estimates
@Service
@Slf4j
public class FlightActualTimeService {
    private final FlightRepository flightRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ActualTimePolicy policy;

    public FlightActualTimeService(
            FlightRepository flightRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            ActualTimePolicy policy) {
        this.flightRepository = flightRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.policy = policy;
    }

    @Transactional
    public Flight recordDeparture(RecordDepartureCommand command) {
        Flight flight = requireFlight(command.getFlightKey());
        // a refusal throws here so nothing is saved and no event is published
        flight.recordDeparture(command.getActualDeparture(), Instant.now(clock), policy);
        Flight saved = flightRepository.save(flight);
        eventPublisher.publishEvent(
                new FlightDeparted(
                        saved.key(),
                        saved.arrivalAirportCode(),
                        saved.aircraftRegistration(),
                        saved.actualDeparture(),
                        Instant.now(clock)));
        log.info(
                "Registered actual departure {} for flight {}",
                saved.actualDeparture(),
                command.getFlightKey());
        return saved;
    }

    @Transactional
    public Flight recordArrival(RecordArrivalCommand command) {
        Flight flight = requireFlight(command.getFlightKey());
        flight.recordArrival(command.getActualArrival(), Instant.now(clock), policy);
        Flight saved = flightRepository.save(flight);
        eventPublisher.publishEvent(
                new FlightLanded(
                        saved.key(),
                        saved.arrivalAirportCode(),
                        saved.aircraftRegistration(),
                        saved.actualArrival(),
                        Instant.now(clock)));
        log.info(
                "Registered actual arrival {} for flight {}",
                saved.actualArrival(),
                command.getFlightKey());
        return saved;
    }

    // the row lock makes a second click wait and then see the registered time, and a cpm
    // delivery for the same flight never interleaves with the departure
    private Flight requireFlight(FlightKey flightKey) {
        return flightRepository
                .findByIdForUpdate(flightKey)
                .orElseThrow(() -> new NotFoundException("Unknown flight " + flightKey));
    }
}
