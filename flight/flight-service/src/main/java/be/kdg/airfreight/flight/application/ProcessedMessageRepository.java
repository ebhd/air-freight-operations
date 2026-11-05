package be.kdg.airfreight.flight.application;

import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.Instant;
import java.util.UUID;

// remembers which cpm messages were applied so a redelivery changes nothing
public interface ProcessedMessageRepository {
    boolean contains(UUID messageId);

    void save(UUID messageId, FlightKey flightKey, Instant processedAt);
}
