package be.kdg.airfreight.cargo.application.booking;

import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import java.util.Optional;

public interface ProcessedMessageRepository {
    Optional<ProcessedMessage> findByMessageId(String messageId);

    void save(ProcessedMessage processedMessage);

    void deleteByFlightKey(FlightKey flightKey);
}
