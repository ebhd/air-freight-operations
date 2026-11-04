package be.kdg.airfreight.cargo.infrastructure.messaging;

import be.kdg.airfreight.cargo.application.cargoplan.CargoEventOutbox;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadFinalized;
import be.kdg.airfreight.cargo.infrastructure.messaging.outbox.OutboxMessage;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaOutboxMessage;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaOutboxMessageRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@Slf4j
public class OutboxCargoEvents implements CargoEventOutbox {
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

    // mandatory fails without a transaction so the row is never written apart from the change
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(LoadFinalized event) {
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
                // 1E+3 becomes 1000 in the json
                new LoadFinalizedMessage.WeightValue(
                        new BigDecimal(uld.weight().value().toPlainString()),
                        uld.weight().unit().name()),
                uld.destination().value());
    }
}
