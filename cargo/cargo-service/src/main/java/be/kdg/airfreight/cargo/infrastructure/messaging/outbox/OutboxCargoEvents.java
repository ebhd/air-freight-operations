package be.kdg.airfreight.cargo.infrastructure.messaging.outbox;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadFinalized;
import be.kdg.airfreight.cargo.infrastructure.messaging.config.CargoEventsConfiguration;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaOutboxMessage;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaOutboxMessageRepository;
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
public class OutboxCargoEvents {
    private final JpaOutboxMessageRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    OutboxCargoEvents(
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
    public void on(LoadFinalized event) {
        LoadFinalizedMessage message =
                new LoadFinalizedMessage(
                        UUID.randomUUID(),
                        LoadFinalizedMessage.VERSION,
                        event.finalizedAt(),
                        event.flightKey().flightNumber(),
                        event.flightKey().scheduledDepartureDate(),
                        event.flightKey().departureAirportCode().value(),
                        event.ulds().stream().map(OutboxCargoEvents::toUld).toList());
        OutboxMessage outboxMessage =
                new OutboxMessage(
                        message.messageId(),
                        CargoEventsConfiguration.CARGO_EVENTS,
                        CargoEventsConfiguration.LOAD_FINALIZED,
                        jsonMapper.writeValueAsString(message),
                        Instant.now(clock),
                        null);
        outboxRepository.save(JpaOutboxMessage.fromDomain(outboxMessage));
        // the publisher hears this only after the commit and then sends at once
        events.publishEvent(outboxMessage);
        log.info(
                "Recorded LoadFinalized message {} in the outbox for flight {}",
                message.messageId(),
                event.flightKey());
    }

    private static LoadFinalizedMessage.Uld toUld(LoadFinalized.FinalizedUld uld) {
        return new LoadFinalizedMessage.Uld(
                uld.code().value(),
                uld.position().name(),
                new LoadFinalizedMessage.WeightValue(
                        uld.weight().plainValue(), uld.weight().unit().name()),
                uld.destination().value());
    }
}
