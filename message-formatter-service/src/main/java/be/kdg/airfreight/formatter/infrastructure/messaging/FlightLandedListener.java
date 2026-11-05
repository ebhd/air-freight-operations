package be.kdg.airfreight.formatter.infrastructure.messaging;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.FlightLandedFact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FlightLandedListener {
    private final MessageFormattingService formattingService;

    public FlightLandedListener(MessageFormattingService formattingService) {
        this.formattingService = formattingService;
    }

    @RabbitListener(queues = RabbitConfiguration.FLIGHT_LANDED_QUEUE)
    public void receive(FlightLandedMessage message) {
        log.info("Received FlightLanded message {}", message.messageId());
        if (message.version() != FlightLandedMessage.VERSION) {
            throw new IllegalArgumentException(
                    "unsupported FlightLanded version: " + message.version());
        }
        if (message.messageId() == null || message.occurredOn() == null) {
            throw new IllegalArgumentException(
                    "FlightLanded must contain a message ID and timestamp");
        }
        var fact =
                new FlightLandedFact(
                        message.flightNumber(),
                        message.scheduledDepartureDate(),
                        message.arrivalAirportCode(),
                        message.aircraftRegistration(),
                        message.actualArrival(),
                        message.occurredOn());
        formattingService.prepareArrival(message.messageId(), fact);
    }
}
