package be.kdg.airfreight.flight.infrastructure.persistence;

import be.kdg.airfreight.flight.application.ProcessedMessageRepository;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaProcessedMessage;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaProcessedMessageRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class DbProcessedMessageRepository implements ProcessedMessageRepository {
    private final JpaProcessedMessageRepository messages;

    DbProcessedMessageRepository(JpaProcessedMessageRepository messages) {
        this.messages = messages;
    }

    @Override
    public boolean contains(UUID messageId) {
        return messages.existsById(messageId);
    }

    @Override
    public void save(UUID messageId, FlightKey flightKey, Instant processedAt) {
        messages.save(new JpaProcessedMessage(messageId, flightKey, processedAt));
    }

    @Override
    public void deleteByFlight(FlightKey flightKey) {
        messages.deleteByFlightNumberAndScheduledDepartureDateAndDepartureAirportCode(
                flightKey.flightNumber(),
                flightKey.scheduledDepartureDate(),
                flightKey.departureAirportCode());
    }
}
