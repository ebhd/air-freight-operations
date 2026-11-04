package be.kdg.airfreight.flight.application;

import be.kdg.airfreight.flight.application.command.ReplanFlightCommand;
import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.delayreason.DelayReasonRepository;
import be.kdg.airfreight.flight.domain.events.FlightReplanned;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightNumberSuffix;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import be.kdg.airfreight.flight.domain.flight.InvalidDelayException;
import be.kdg.airfreight.flight.domain.flight.Replanning;
import be.kdg.airfreight.flight.domain.flight.ReplanningRefusedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class FlightReplanningService {
    private final FlightRepository flightRepository;
    private final DelayReasonRepository delayReasonRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public FlightReplanningService(
            FlightRepository flightRepository,
            DelayReasonRepository delayReasonRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.flightRepository = flightRepository;
        this.delayReasonRepository = delayReasonRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // ends the old flight and asks the planning system for the new one, in one transaction
    @Transactional
    public Flight replan(ReplanFlightCommand command) {
        // the row lock makes a second click wait and then get NOT_PLANNED
        Flight flight =
                flightRepository
                        .findByIdForUpdate(command.getFlightKey())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Unknown flight " + command.getFlightKey()));
        if (command.getDelayId() == null) {
            requireDelayReason(command.getReasonCode());
        }
        String newFlightNumber = freeFlightNumber(flight, command);
        Replanning replanning =
                flight.replan(
                        command.getDelayType(),
                        command.getDelayId(),
                        command.getMinutes(),
                        command.getReasonCode(),
                        command.getDescription(),
                        newFlightNumber,
                        Instant.now(clock));
        Flight saved = flightRepository.save(flight);
        eventPublisher.publishEvent(
                new FlightReplanned(
                        saved.key(),
                        saved.arrivalAirportCode(),
                        saved.aircraftRegistration(),
                        saved.scheduledDeparture(),
                        saved.scheduledArrival(),
                        replanning,
                        replanning.replannedAt()));
        log.info(
                "Replanned flight {} as {} departing {}",
                saved.key(),
                replanning.newKey(saved.key().departureAirportCode()),
                replanning.newScheduledDeparture());
        return saved;
    }

    // the same calculation as replan but nothing is saved, for the 409 of a refused delay
    // throws ReplanningRefusedException when the replan itself would be refused
    public Replanning propose(Flight flight, ReplanFlightCommand command) {
        return proposal(flight, command, freeFlightNumber(flight, command));
    }

    // the next letter that is still free on the new date (decision 8)
    private String freeFlightNumber(Flight flight, ReplanFlightCommand command) {
        String oldNumber = flight.key().flightNumber();
        Optional<String> candidate = FlightNumberSuffix.next(oldNumber);
        if (candidate.isEmpty()) {
            throw noFreeFlightNumber(oldNumber, null);
        }
        // the new date never depends on the number so the first candidate already gives it
        LocalDate newDate = proposal(flight, command, candidate.get()).newScheduledDepartureDate();
        while (candidate.isPresent()) {
            FlightKey newKey =
                    new FlightKey(candidate.get(), newDate, flight.key().departureAirportCode());
            if (flightRepository.findById(newKey).isEmpty()) {
                return candidate.get();
            }
            log.info("Flight {} exists, trying the next letter for {}", newKey, flight.key());
            candidate = FlightNumberSuffix.next(candidate.get());
        }
        throw noFreeFlightNumber(oldNumber, newDate);
    }

    private static ReplanningRefusedException noFreeFlightNumber(
            String oldNumber, LocalDate newDate) {
        return new ReplanningRefusedException(
                ReplanningRefusedException.NO_FREE_FLIGHT_NUMBER,
                "no free flight number for "
                        + oldNumber
                        + (newDate == null ? "" : " on " + newDate)
                        + ", every letter up to Z is taken");
    }

    private Replanning proposal(Flight flight, ReplanFlightCommand command, String number) {
        return flight.proposeReplanning(
                command.getDelayType(),
                command.getDelayId(),
                command.getMinutes(),
                command.getReasonCode(),
                command.getDescription(),
                number,
                Instant.now(clock));
    }

    private void requireDelayReason(String reasonCode) {
        delayReasonRepository
                .findByCode(reasonCode)
                .orElseThrow(
                        () -> new InvalidDelayException("Unknown delay reason code " + reasonCode));
    }
}
