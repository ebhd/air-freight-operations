package be.kdg.airfreight.flight.infrastructure.messaging.events;

import be.kdg.airfreight.flight.domain.events.FlightCreated;
import be.kdg.airfreight.flight.domain.events.FlightDeparted;
import be.kdg.airfreight.flight.domain.events.FlightEstimatesChanged;
import be.kdg.airfreight.flight.domain.events.FlightLanded;
import be.kdg.airfreight.flight.domain.events.FlightRemoved;
import be.kdg.airfreight.flight.domain.events.FlightReplaced;
import be.kdg.airfreight.flight.domain.events.FlightReplanned;
import be.kdg.airfreight.flight.infrastructure.messaging.RabbitConfiguration;
import be.kdg.airfreight.flight.infrastructure.messaging.outbox.OutboxMessage;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaOutboxMessage;
import be.kdg.airfreight.flight.infrastructure.persistence.jpa.JpaOutboxMessageRepository;
import java.time.Clock;
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
    private final Clock clock;

    public FlightEventPublisher(
            JpaOutboxMessageRepository outboxRepository,
            JsonMapper jsonMapper,
            ApplicationEventPublisher events,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.events = events;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(FlightCreated event) {

        var message =
                new FlightCreatedMessage(
                        UUID.randomUUID(),
                        FlightCreatedMessage.VERSION,
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
                        Instant.now(clock),
                        null);

        outboxRepository.save(JpaOutboxMessage.fromDomain(outboxMessage));
        events.publishEvent(outboxMessage);

        log.debug(
                "Recorded FlightCreated message {} in outbox for flight {}",
                message.messageId(),
                event.flightKey());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(FlightEstimatesChanged event) {
        var message =
                new FlightEstimatesChangedMessage(
                        UUID.randomUUID(),
                        FlightEstimatesChangedMessage.VERSION,
                        event.occurredOn(),
                        event.flightKey().flightNumber(),
                        event.flightKey().scheduledDepartureDate(),
                        event.flightKey().departureAirportCode(),
                        event.arrivalAirportCode(),
                        event.aircraftRegistration(),
                        event.previousEstimatedDeparture(),
                        event.previousEstimatedArrival(),
                        event.estimatedDeparture(),
                        event.estimatedArrival());

        var outboxMessage =
                new OutboxMessage(
                        message.messageId(),
                        RabbitConfiguration.FLIGHT_EVENTS,
                        RabbitConfiguration.FLIGHT_ESTIMATES_CHANGED,
                        jsonMapper.writeValueAsString(message),
                        Instant.now(clock),
                        null);

        outboxRepository.save(JpaOutboxMessage.fromDomain(outboxMessage));
        events.publishEvent(outboxMessage);
        log.debug(
                "Recorded FlightEstimatesChanged message {} in outbox for flight {}",
                message.messageId(),
                event.flightKey());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(FlightDeparted event) {
        var message =
                new FlightDepartedMessage(
                        UUID.randomUUID(),
                        FlightDepartedMessage.VERSION,
                        event.occurredOn(),
                        event.flightKey().flightNumber(),
                        event.flightKey().scheduledDepartureDate(),
                        event.flightKey().departureAirportCode(),
                        event.arrivalAirportCode(),
                        event.aircraftRegistration(),
                        event.actualDeparture());

        var outboxMessage =
                new OutboxMessage(
                        message.messageId(),
                        RabbitConfiguration.FLIGHT_EVENTS,
                        RabbitConfiguration.FLIGHT_DEPARTED,
                        jsonMapper.writeValueAsString(message),
                        Instant.now(clock),
                        null);

        outboxRepository.save(JpaOutboxMessage.fromDomain(outboxMessage));
        events.publishEvent(outboxMessage);
        log.debug(
                "Recorded FlightDeparted message {} in outbox for flight {}",
                message.messageId(),
                event.flightKey());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(FlightLanded event) {
        var message =
                new FlightLandedMessage(
                        UUID.randomUUID(),
                        FlightLandedMessage.VERSION,
                        event.occurredOn(),
                        event.flightKey().flightNumber(),
                        event.flightKey().scheduledDepartureDate(),
                        event.flightKey().departureAirportCode(),
                        event.arrivalAirportCode(),
                        event.aircraftRegistration(),
                        event.actualArrival());

        var outboxMessage =
                new OutboxMessage(
                        message.messageId(),
                        RabbitConfiguration.FLIGHT_EVENTS,
                        RabbitConfiguration.FLIGHT_LANDED,
                        jsonMapper.writeValueAsString(message),
                        Instant.now(clock),
                        null);

        outboxRepository.save(JpaOutboxMessage.fromDomain(outboxMessage));
        events.publishEvent(outboxMessage);
        log.debug(
                "Recorded FlightLanded message {} in outbox for flight {}",
                message.messageId(),
                event.flightKey());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(FlightReplanned event) {
        var message =
                new FlightReplannedMessage(
                        UUID.randomUUID(),
                        FlightReplannedMessage.VERSION,
                        event.occurredOn(),
                        event.flightKey().flightNumber(),
                        event.flightKey().scheduledDepartureDate(),
                        event.flightKey().departureAirportCode(),
                        event.arrivalAirportCode(),
                        event.aircraftRegistration(),
                        event.scheduledDeparture(),
                        event.scheduledArrival(),
                        event.replanning().newFlightNumber(),
                        event.replanning().newScheduledDepartureDate(),
                        event.replanning().newScheduledDeparture(),
                        event.replanning().newScheduledArrival());

        var outboxMessage =
                new OutboxMessage(
                        message.messageId(),
                        RabbitConfiguration.FLIGHT_EVENTS,
                        RabbitConfiguration.FLIGHT_REPLANNED,
                        jsonMapper.writeValueAsString(message),
                        Instant.now(clock),
                        null);

        outboxRepository.save(JpaOutboxMessage.fromDomain(outboxMessage));
        events.publishEvent(outboxMessage);
        log.debug(
                "Recorded FlightReplanned message {} in outbox for flight {}",
                message.messageId(),
                event.flightKey());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(FlightReplaced event) {
        var message =
                new FlightReplacedMessage(
                        UUID.randomUUID(),
                        FlightReplacedMessage.VERSION,
                        event.occurredOn(),
                        event.flightKey().flightNumber(),
                        event.flightKey().scheduledDepartureDate(),
                        event.flightKey().departureAirportCode(),
                        event.newFlightKey().flightNumber(),
                        event.newFlightKey().scheduledDepartureDate(),
                        event.newFlightKey().departureAirportCode(),
                        event.newScheduledDeparture(),
                        event.newScheduledArrival());

        var outboxMessage =
                new OutboxMessage(
                        message.messageId(),
                        RabbitConfiguration.FLIGHT_EVENTS,
                        RabbitConfiguration.FLIGHT_REPLACED,
                        jsonMapper.writeValueAsString(message),
                        Instant.now(clock),
                        null);

        outboxRepository.save(JpaOutboxMessage.fromDomain(outboxMessage));
        events.publishEvent(outboxMessage);
        log.debug(
                "Recorded FlightReplaced message {} in outbox for flight {}",
                message.messageId(),
                event.flightKey());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(FlightRemoved event) {
        var message =
                new FlightRemovedMessage(
                        UUID.randomUUID(),
                        FlightRemovedMessage.VERSION,
                        event.occurredOn(),
                        event.flightKey().flightNumber(),
                        event.flightKey().scheduledDepartureDate(),
                        event.flightKey().departureAirportCode());

        var outboxMessage =
                new OutboxMessage(
                        message.messageId(),
                        RabbitConfiguration.FLIGHT_EVENTS,
                        RabbitConfiguration.FLIGHT_REMOVED,
                        jsonMapper.writeValueAsString(message),
                        Instant.now(clock),
                        null);

        outboxRepository.save(JpaOutboxMessage.fromDomain(outboxMessage));
        events.publishEvent(outboxMessage);
        log.debug(
                "Recorded FlightRemoved message {} in outbox for flight {}",
                message.messageId(),
                event.flightKey());
    }
}
