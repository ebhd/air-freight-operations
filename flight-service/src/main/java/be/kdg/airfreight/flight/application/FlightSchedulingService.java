package be.kdg.airfreight.flight.application;

import be.kdg.airfreight.flight.domain.Flight;
import be.kdg.airfreight.flight.domain.FlightRepository;
import be.kdg.airfreight.flight.domain.FlightSchedule;
import be.kdg.airfreight.flight.domain.events.FlightCreated;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class FlightSchedulingService {
    private final FlightRepository flightRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FlightSchedulingService(
            FlightRepository flightRepository, ApplicationEventPublisher eventPublisher) {
        this.flightRepository = flightRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProcessSsmResult createFlights(String messageId, FlightSchedule schedule) {
        requireMessageId(messageId);

        List<Flight> flights = schedule.expand();

        var created = new ArrayList<Flight>();
        var duplicateKeys = new ArrayList<String>();

        for (Flight flight : flights) {
            if (flightRepository.findById(flight.key()).isPresent()) {
                duplicateKeys.add(flight.key().toString());
                log.info("Skipping existing flight {} from SSM {}", flight.key(), messageId);
                continue;
            }
            Flight saved = flightRepository.save(flight);
            created.add(saved);

            FlightCreated event =
                    new FlightCreated(
                            saved.key(),
                            saved.arrivalAirportCode(),
                            saved.scheduledDeparture(),
                            saved.scheduledArrival(),
                            Instant.now());
            eventPublisher.publishEvent(event);
        }
        return new ProcessSsmResult(messageId, created, duplicateKeys);
    }

    private static void requireMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("message ID must not be blank");
        }
    }
}
