package be.kdg.airfreight.formatter.infrastructure.messaging;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.FlightEstimatesChangedFact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FlightEstimatesChangedListener {
    private final MessageFormattingService formattingService;

    public FlightEstimatesChangedListener(MessageFormattingService formattingService) {
        this.formattingService = formattingService;
    }

    @RabbitListener(queues = RabbitConfiguration.FLIGHT_ESTIMATES_CHANGED_QUEUE)
    public void receive(FlightEstimatesChangedMessage message) {
        log.info("Received FlightEstimatesChanged message {}", message.messageId());
        if (message.version() != FlightEstimatesChangedMessage.VERSION) {
            throw new IllegalArgumentException(
                    "unsupported FlightEstimatesChanged version: " + message.version());
        }
        if (message.messageId() == null || message.occurredOn() == null) {
            throw new IllegalArgumentException(
                    "FlightEstimatesChanged must contain a message ID and timestamp");
        }
        var fact =
                new FlightEstimatesChangedFact(
                        message.flightNumber(),
                        message.scheduledDepartureDate(),
                        message.departureAirportCode(),
                        message.arrivalAirportCode(),
                        message.aircraftRegistration(),
                        message.previousEstimatedDeparture(),
                        message.previousEstimatedArrival(),
                        message.estimatedDeparture(),
                        message.estimatedArrival(),
                        message.occurredOn());
        formattingService.prepareEstimates(message.messageId(), fact);
    }
}
