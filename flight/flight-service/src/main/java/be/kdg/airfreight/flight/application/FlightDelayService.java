package be.kdg.airfreight.flight.application;

import be.kdg.airfreight.flight.application.command.AddDelayCommand;
import be.kdg.airfreight.flight.application.command.UpdateDelayCommand;
import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.delayreason.DelayReasonRepository;
import be.kdg.airfreight.flight.domain.events.FlightEstimatesChanged;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import be.kdg.airfreight.flight.domain.flight.InvalidDelayException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class FlightDelayService {
    private final FlightRepository flightRepository;
    private final DelayReasonRepository delayReasonRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public FlightDelayService(
            FlightRepository flightRepository,
            DelayReasonRepository delayReasonRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.flightRepository = flightRepository;
        this.delayReasonRepository = delayReasonRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Flight addDelay(AddDelayCommand command) {
        Flight flight = requireFlight(command.getFlightKey());
        Instant previousDeparture = flight.estimatedDeparture();
        Instant previousArrival = flight.estimatedArrival();

        delayReasonRepository
                .findByCode(command.getReasonCode())
                .orElseThrow(
                        () ->
                                new InvalidDelayException(
                                        "Unknown delay reason code " + command.getReasonCode()));

        flight.addDelay(
                command.getType(),
                command.getMinutes(),
                command.getReasonCode(),
                command.getDescription());

        Flight saved = saveAndPublishEstimatesChange(flight, previousDeparture, previousArrival);
        log.info("Added {} delay to flight {}", command.getType(), command.getFlightKey());
        return saved;
    }

    @Transactional
    public void updateDelay(UpdateDelayCommand command) {
        Flight flight = requireFlight(command.getFlightKey());
        Instant previousDeparture = flight.estimatedDeparture();
        Instant previousArrival = flight.estimatedArrival();
        flight.updateDelay(command.getDelayId(), command.getMinutes(), command.getDescription());
        saveAndPublishEstimatesChange(flight, previousDeparture, previousArrival);
        log.info("Updated delay {} for flight {}", command.getDelayId(), command.getFlightKey());
    }

    @Transactional
    public void removeDelay(FlightKey flightKey, UUID delayId) {
        Flight flight = requireFlight(flightKey);
        Instant previousDeparture = flight.estimatedDeparture();
        Instant previousArrival = flight.estimatedArrival();
        flight.removeDelay(delayId);
        saveAndPublishEstimatesChange(flight, previousDeparture, previousArrival);
        log.info("Removed delay {} from flight {}", delayId, flightKey);
    }

    private Flight saveAndPublishEstimatesChange(
            Flight flight, Instant previousDeparture, Instant previousArrival) {

        Flight saved = flightRepository.save(flight);

        if (!previousDeparture.equals(saved.estimatedDeparture())
                || !previousArrival.equals(saved.estimatedArrival())) {

            eventPublisher.publishEvent(
                    new FlightEstimatesChanged(
                            saved.key(),
                            saved.arrivalAirportCode(),
                            saved.aircraftRegistration(),
                            previousDeparture,
                            previousArrival,
                            saved.estimatedDeparture(),
                            saved.estimatedArrival(),
                            Instant.now(clock)));
        }
        return saved;
    }

    // every delay change locks the flight so it starts from the last saved estimates
    private Flight requireFlight(FlightKey flightKey) {
        return flightRepository
                .findByIdForUpdate(flightKey)
                .orElseThrow(() -> new NotFoundException("Unknown flight " + flightKey));
    }
}
