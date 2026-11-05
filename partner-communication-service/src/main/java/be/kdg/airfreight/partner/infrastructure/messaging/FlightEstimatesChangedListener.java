package be.kdg.airfreight.partner.infrastructure.messaging;

import be.kdg.airfreight.partner.application.PartnerMessageService;
import be.kdg.airfreight.partner.domain.FlightEstimatesChangedFact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FlightEstimatesChangedListener {
    private final PartnerMessageService partnerMessageService;

    public FlightEstimatesChangedListener(PartnerMessageService partnerMessageService) {
        this.partnerMessageService = partnerMessageService;
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
        partnerMessageService.prepareEstimates(message.messageId(), fact);
    }
}
