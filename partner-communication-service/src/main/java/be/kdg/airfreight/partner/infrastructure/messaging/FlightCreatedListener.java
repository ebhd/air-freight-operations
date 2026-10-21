package be.kdg.airfreight.partner.infrastructure.messaging;

import be.kdg.airfreight.partner.application.PartnerMessageService;
import be.kdg.airfreight.partner.domain.FlightCreatedFact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FlightCreatedListener {
    private final PartnerMessageService partnerMessageService;

    public FlightCreatedListener(PartnerMessageService partnerMessageService) {
        this.partnerMessageService = partnerMessageService;
    }

    @RabbitListener(queues = RabbitConfiguration.FLIGHT_CREATED_QUEUE)
    public void receive(FlightCreatedMessage message) {
        log.info("Received FlightCreated message {}", message.messageId());
        if (message.version() != 1) {
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
        partnerMessageService.prepareFlm(message.messageId(), fact);
    }
}
