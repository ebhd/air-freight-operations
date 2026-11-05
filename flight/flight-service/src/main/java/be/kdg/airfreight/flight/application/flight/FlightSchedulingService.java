package be.kdg.airfreight.flight.application.flight;

import be.kdg.airfreight.flight.domain.events.FlightCreated;
import be.kdg.airfreight.flight.domain.events.FlightReplaced;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import be.kdg.airfreight.flight.domain.flight.FlightSchedule;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class FlightSchedulingService {
    private final FlightRepository flightRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public FlightSchedulingService(
            FlightRepository flightRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.flightRepository = flightRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
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
            // a replanned flight that expects exactly this key and std is linked in the same
            // transaction, so the new flight, both links and both events commit together
            Optional<Flight> replanned =
                    flightRepository.findReplacedBy(flight.key(), flight.scheduledDeparture());
            replanned.ifPresent(old -> flight.recordReplaces(old.key()));
            Flight saved = flightRepository.save(flight);
            created.add(saved);

            Instant now = Instant.now(clock);
            FlightCreated event =
                    new FlightCreated(
                            saved.key(),
                            saved.arrivalAirportCode(),
                            saved.scheduledDeparture(),
                            saved.scheduledArrival(),
                            now);
            eventPublisher.publishEvent(event);
            replanned.ifPresent(old -> linkReplacement(old, saved, now));
        }
        return new ProcessSsmResult(messageId, created, duplicateKeys);
    }

    private void linkReplacement(Flight replanned, Flight replacement, Instant now) {
        replanned.recordReplacementCreated();
        flightRepository.save(replanned);
        eventPublisher.publishEvent(
                new FlightReplaced(
                        replanned.key(),
                        replacement.key(),
                        replacement.scheduledDeparture(),
                        replacement.scheduledArrival(),
                        now));
        log.info("Flight {} replaces replanned flight {}", replacement.key(), replanned.key());
    }

    private static void requireMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("message ID must not be blank");
        }
    }
}
