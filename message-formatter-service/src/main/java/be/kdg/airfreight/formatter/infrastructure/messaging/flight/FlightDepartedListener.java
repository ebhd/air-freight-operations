package be.kdg.airfreight.formatter.infrastructure.messaging.flight;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.FlightDepartedFact;
import be.kdg.airfreight.formatter.infrastructure.messaging.RabbitConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FlightDepartedListener {
    private final MessageFormattingService formattingService;

    public FlightDepartedListener(MessageFormattingService formattingService) {
        this.formattingService = formattingService;
    }

    // an invalid message throws and ends up in partner.dlq, a publish failure is retried
    @RabbitListener(queues = RabbitConfiguration.FLIGHT_DEPARTED_QUEUE)
    public void receive(FlightDepartedMessage message) {
        log.info("Received FlightDeparted message {}", message.messageId());
        if (message.version() != FlightDepartedMessage.VERSION) {
            throw new IllegalArgumentException(
                    "unsupported FlightDeparted version: " + message.version());
        }
        if (message.messageId() == null || message.occurredOn() == null) {
            throw new IllegalArgumentException(
                    "FlightDeparted must contain a message ID and timestamp");
        }
        var fact =
                new FlightDepartedFact(
                        message.flightNumber(),
                        message.scheduledDepartureDate(),
                        message.departureAirportCode(),
                        message.aircraftRegistration(),
                        message.actualDeparture(),
                        message.occurredOn());
        formattingService.prepareDeparture(message.messageId(), fact);
    }
}
