package be.kdg.airfreight.formatter.infrastructure.messaging.flight;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.FlightCreatedFact;
import be.kdg.airfreight.formatter.infrastructure.messaging.RabbitConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FlightCreatedListener {
    private final MessageFormattingService formattingService;

    public FlightCreatedListener(MessageFormattingService formattingService) {
        this.formattingService = formattingService;
    }

    @RabbitListener(queues = RabbitConfiguration.FLIGHT_CREATED_QUEUE)
    public void receive(FlightCreatedMessage message) {
        log.info("Received FlightCreated message {}", message.messageId());
        if (message.version() != FlightCreatedMessage.VERSION) {
            throw new IllegalArgumentException(
                    "unsupported FlightCreated version: " + message.version());
        }
        if (message.messageId() == null || message.occurredOn() == null) {
            throw new IllegalArgumentException(
                    "FlightCreated must contain a message ID and timestamp");
        }
        var fact =
                new FlightCreatedFact(
                        message.flightNumber(),
                        message.scheduledDepartureDate(),
                        message.departureAirportCode(),
                        message.arrivalAirportCode(),
                        message.scheduledDeparture(),
                        message.scheduledArrival(),
                        message.occurredOn());
        formattingService.prepareFlm(message.messageId(), fact);
    }
}
