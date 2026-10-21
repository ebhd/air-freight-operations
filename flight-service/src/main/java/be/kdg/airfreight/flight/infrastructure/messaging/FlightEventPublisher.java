package be.kdg.airfreight.flight.infrastructure.messaging;

import be.kdg.airfreight.flight.domain.events.FlightCreated;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaOutboxMessage;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaOutboxMessageRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

@Component
@Slf4j
public class FlightEventPublisher {
    private final JpaOutboxMessageRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher events;

    public FlightEventPublisher(
            JpaOutboxMessageRepository outboxRepository,
            JsonMapper jsonMapper,
            ApplicationEventPublisher events) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.events = events;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(FlightCreated event) {

        var message =
                new FlightCreatedMessage(
                        UUID.randomUUID(),
                        1,
                        event.occurredOn(),
                        event.flightKey().flightNumber(),
                        event.flightKey().scheduledDepartureDate(),
                        event.flightKey().departureAirportCode(),
                        event.arrivalAirportCode(),
                        event.scheduledDeparture(),
                        event.scheduledArrival());

        var outboxMessage =
                new OutboxMessage(
                        message.messageId(),
                        RabbitConfiguration.FLIGHT_EVENTS,
                        RabbitConfiguration.FLIGHT_CREATED,
                        jsonMapper.writeValueAsString(message),
                        Instant.now(),
                        null);

        outboxRepository.save(JpaOutboxMessage.fromDomain(outboxMessage));
        events.publishEvent(outboxMessage);

        log.debug(
                "Recorded FlightCreated message {} in outbox for flight {}",
                message.messageId(),
                event.flightKey());
    }
}
