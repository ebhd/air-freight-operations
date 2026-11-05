package be.kdg.airfreight.formatter.infrastructure.messaging.flight;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.FlightReplacedFact;
import be.kdg.airfreight.formatter.infrastructure.messaging.RabbitConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FlightReplacedListener {
    private final MessageFormattingService formattingService;

    public FlightReplacedListener(MessageFormattingService formattingService) {
        this.formattingService = formattingService;
    }

    @RabbitListener(queues = RabbitConfiguration.FLIGHT_REPLACED_QUEUE)
    public void receive(FlightReplacedMessage message) {
        log.info("Received FlightReplaced message {}", message.messageId());
        if (message.version() != FlightReplacedMessage.VERSION) {
            throw new IllegalArgumentException(
                    "unsupported FlightReplaced version: " + message.version());
        }
        if (message.messageId() == null || message.occurredOn() == null) {
            throw new IllegalArgumentException(
                    "FlightReplaced must contain a message ID and timestamp");
        }
        var fact =
                new FlightReplacedFact(
                        message.flightNumber(),
                        message.scheduledDepartureDate(),
                        message.departureAirportCode(),
                        message.newFlightNumber(),
                        message.newScheduledDepartureDate(),
                        message.newDepartureAirportCode(),
                        message.newScheduledDeparture(),
                        message.newScheduledArrival(),
                        message.occurredOn());
        formattingService.prepareFlightReplanned(message.messageId(), fact);
    }
}
