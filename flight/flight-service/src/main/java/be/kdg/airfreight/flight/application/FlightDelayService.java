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
    public Flight addDepartureDelay(AddDelayCommand command) {
        Flight flight = requireFlight(command.getFlightKey());
        Instant previousDeparture = flight.estimatedDeparture();
        Instant previousArrival = flight.estimatedArrival();
        requireDelayReason(command.getReasonCode());
        flight.addDepartureDelay(
                command.getMinutes(), command.getReasonCode(), command.getDescription());
        Flight saved = saveAndPublishEstimatesChange(flight, previousDeparture, previousArrival);
        log.info("Added departure delay to flight {}", command.getFlightKey());
        return saved;
    }

    @Transactional
    public void updateDepartureDelay(UpdateDelayCommand command) {
        Flight flight = requireFlight(command.getFlightKey());
        Instant previousDeparture = flight.estimatedDeparture();
        Instant previousArrival = flight.estimatedArrival();
        flight.updateDepartureDelay(
                command.getDelayId(), command.getMinutes(), command.getDescription());
        saveAndPublishEstimatesChange(flight, previousDeparture, previousArrival);
        log.info(
                "Updated departure delay {} for flight {}",
                command.getDelayId(),
                command.getFlightKey());
    }

    @Transactional
    public void removeDepartureDelay(FlightKey flightKey, UUID delayId) {
        Flight flight = requireFlight(flightKey);
        Instant previousDeparture = flight.estimatedDeparture();
        Instant previousArrival = flight.estimatedArrival();
        flight.removeDepartureDelay(delayId);
        saveAndPublishEstimatesChange(flight, previousDeparture, previousArrival);
        log.info("Removed departure delay {} from flight {}", delayId, flightKey);
    }

    @Transactional
    public Flight addArrivalDelay(AddDelayCommand command) {
        Flight flight = requireFlight(command.getFlightKey());
        Instant previousDeparture = flight.estimatedDeparture();
        Instant previousArrival = flight.estimatedArrival();
        requireDelayReason(command.getReasonCode());
        flight.addArrivalDelay(
                command.getMinutes(), command.getReasonCode(), command.getDescription());
        Flight saved = saveAndPublishEstimatesChange(flight, previousDeparture, previousArrival);
        log.info("Added arrival delay to flight {}", command.getFlightKey());
        return saved;
    }

    @Transactional
    public void updateArrivalDelay(UpdateDelayCommand command) {
        Flight flight = requireFlight(command.getFlightKey());
        Instant previousDeparture = flight.estimatedDeparture();
        Instant previousArrival = flight.estimatedArrival();
        flight.updateArrivalDelay(
                command.getDelayId(), command.getMinutes(), command.getDescription());
        saveAndPublishEstimatesChange(flight, previousDeparture, previousArrival);
        log.info(
                "Updated arrival delay {} for flight {}",
                command.getDelayId(),
                command.getFlightKey());
    }

    @Transactional
    public void removeArrivalDelay(FlightKey flightKey, UUID delayId) {
        Flight flight = requireFlight(flightKey);
        Instant previousDeparture = flight.estimatedDeparture();
        Instant previousArrival = flight.estimatedArrival();
        flight.removeArrivalDelay(delayId);
        saveAndPublishEstimatesChange(flight, previousDeparture, previousArrival);
        log.info("Removed arrival delay {} from flight {}", delayId, flightKey);
    }

    private void requireDelayReason(String reasonCode) {
        delayReasonRepository
                .findByCode(reasonCode)
                .orElseThrow(
                        () -> new InvalidDelayException("Unknown delay reason code " + reasonCode));
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
