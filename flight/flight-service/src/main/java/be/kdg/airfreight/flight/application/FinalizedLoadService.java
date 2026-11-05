package be.kdg.airfreight.flight.application;

import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.FlightRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class FinalizedLoadService {
    private final FlightRepository flightRepository;
    private final ProcessedMessageRepository processedMessages;
    private final Clock clock;

    public FinalizedLoadService(
            FlightRepository flightRepository,
            ProcessedMessageRepository processedMessages,
            Clock clock) {
        this.flightRepository = flightRepository;
        this.processedMessages = processedMessages;
        this.clock = clock;
    }

    // true when this message set the flag, false for a redelivery or an already processed load
    @Transactional
    public boolean recordFinalizedLoad(UUID messageId, FlightKey flightKey) {
        // the row lock makes a delay change or a second delivery for this flight wait
        Flight flight =
                flightRepository
                        .findByIdForUpdate(flightKey)
                        .orElseThrow(() -> new NotFoundException("Unknown flight " + flightKey));
        if (processedMessages.contains(messageId)) {
            log.info("Ignored redelivered CPM message {} for flight {}", messageId, flightKey);
            return false;
        }
        boolean recorded = flight.recordFinalizedLoad();
        if (recorded) {
            flightRepository.save(flight);
            log.info("Processed CPM message {}: flight {} may depart", messageId, flightKey);
        } else {
            log.info(
                    "Flight {} already had a processed CPM, message {} changes nothing",
                    flightKey,
                    messageId);
        }
        processedMessages.save(messageId, flightKey, Instant.now(clock));
        return recorded;
    }
}
