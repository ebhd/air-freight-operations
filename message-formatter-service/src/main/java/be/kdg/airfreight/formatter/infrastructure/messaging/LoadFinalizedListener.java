package be.kdg.airfreight.formatter.infrastructure.messaging;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.LoadFinalizedFact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LoadFinalizedListener {
    private final MessageFormattingService formattingService;

    public LoadFinalizedListener(MessageFormattingService formattingService) {
        this.formattingService = formattingService;
    }

    @RabbitListener(queues = RabbitConfiguration.LOAD_FINALIZED_QUEUE)
    public void receive(LoadFinalizedMessage message) {
        log.info("Received LoadFinalized message {}", message.messageId());
        if (message.version() != LoadFinalizedMessage.VERSION) {
            throw new IllegalArgumentException(
                    "unsupported LoadFinalized version: " + message.version());
        }
        if (message.messageId() == null || message.occurredOn() == null) {
            throw new IllegalArgumentException(
                    "LoadFinalized must contain a message ID and timestamp");
        }
        if (message.ulds() == null) {
            throw new IllegalArgumentException("LoadFinalized must contain a ULD list");
        }
        var fact =
                new LoadFinalizedFact(
                        message.flightNumber(),
                        message.scheduledDepartureDate(),
                        message.departureAirportCode(),
                        message.ulds().stream().map(LoadFinalizedListener::toFact).toList(),
                        message.occurredOn());
        formattingService.prepareCpm(message.messageId(), fact);
    }

    private static LoadFinalizedFact.FinalizedUld toFact(LoadFinalizedMessage.Uld uld) {
        if (uld == null || uld.weight() == null) {
            throw new IllegalArgumentException("every ULD must contain a weight");
        }
        return new LoadFinalizedFact.FinalizedUld(
                uld.uldCode(),
                uld.position(),
                uld.weight().value(),
                uld.weight().unit(),
                uld.destinationAirportCode());
    }
}
