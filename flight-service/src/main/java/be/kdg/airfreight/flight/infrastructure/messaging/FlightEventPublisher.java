package be.kdg.airfreight.flight.infrastructure.messaging;

import be.kdg.airfreight.flight.domain.events.FlightCreated;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class FlightEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public FlightEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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

        rabbitTemplate.convertAndSend(
                RabbitConfiguration.FLIGHT_EVENTS, RabbitConfiguration.FLIGHT_CREATED, message);
        log.debug(
                "Sent FlightCreated message {} for flight {}",
                message.messageId(),
                event.flightKey());
    }
}
