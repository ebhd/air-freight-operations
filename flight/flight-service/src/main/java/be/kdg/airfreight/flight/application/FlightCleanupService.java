package be.kdg.airfreight.flight.application;

import be.kdg.airfreight.flight.domain.events.FlightRemoved;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import be.kdg.airfreight.flight.domain.flight.RetentionPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// removes flights past their retention, whatever their status
@Service
@Slf4j
public class FlightCleanupService {
    private final FlightRepository flightRepository;
    private final ProcessedMessageRepository processedMessages;
    private final ApplicationEventPublisher eventPublisher;
    private final RetentionPolicy retentionPolicy;
    private final Clock clock;

    public FlightCleanupService(
            FlightRepository flightRepository,
            ProcessedMessageRepository processedMessages,
            ApplicationEventPublisher eventPublisher,
            RetentionPolicy retentionPolicy,
            Clock clock) {
        this.flightRepository = flightRepository;
        this.processedMessages = processedMessages;
        this.eventPublisher = eventPublisher;
        this.retentionPolicy = retentionPolicy;
        this.clock = clock;
    }

    public List<FlightKey> expiredFlights() {
        return flightRepository.findKeysUpTo(retentionPolicy.lastExpiredDate(Instant.now(clock)));
    }

    // one transaction per flight: flight, delays, dedup rows and the event go together or not
    @Transactional
    public void remove(FlightKey flightKey) {
        // the row lock lets a running delay change or cpm finish first
        if (flightRepository.findByIdForUpdate(flightKey).isEmpty()) {
            return;
        }
        // a redelivered cpm finds no flight and is rejected, so the dedup rows have no use anymore
        processedMessages.deleteByFlight(flightKey);
        flightRepository.delete(flightKey);
        eventPublisher.publishEvent(new FlightRemoved(flightKey, Instant.now(clock)));
        log.info("Removed flight {}: its retention has passed", flightKey);
    }
}
