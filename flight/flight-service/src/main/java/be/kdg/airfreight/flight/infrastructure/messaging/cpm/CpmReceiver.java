package be.kdg.airfreight.flight.infrastructure.messaging.cpm;

import be.kdg.airfreight.flight.application.finalizedload.FinalizedLoadService;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.infrastructure.messaging.RabbitConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// anti-corruption layer: translates cargo's finalized load into our own departure condition
@Component
@Slf4j
public class CpmReceiver {
    private final FinalizedLoadService finalizedLoadService;

    public CpmReceiver(FinalizedLoadService finalizedLoadService) {
        this.finalizedLoadService = finalizedLoadService;
    }

    // an exception here ends in flight.dlq after the retries, a database outage is requeued
    @RabbitListener(queues = RabbitConfiguration.CARGO_LOAD_FINALIZED_QUEUE)
    public void receive(LoadFinalizedMessage message) {
        log.info("Received LoadFinalized message {}", message.messageId());
        try {
            if (message.version() != LoadFinalizedMessage.VERSION) {
                throw new IllegalArgumentException(
                        "unsupported LoadFinalized version: " + message.version());
            }
            if (message.messageId() == null || message.occurredOn() == null) {
                throw new IllegalArgumentException(
                        "LoadFinalized must contain a message ID and timestamp");
            }
            // the flight key checks the flight number, date and airport code
            FlightKey flightKey =
                    new FlightKey(
                            message.flightNumber(),
                            message.scheduledDepartureDate(),
                            message.departureAirportCode());
            finalizedLoadService.recordFinalizedLoad(message.messageId(), flightKey);
        } catch (RuntimeException rejected) {
            log.warn(
                    "LoadFinalized message {} not applied: {}",
                    message.messageId(),
                    rejected.getMessage());
            throw rejected;
        }
    }
}
