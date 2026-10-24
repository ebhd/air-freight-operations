package be.kdg.airfreight.simulator.uld;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class UldPublisher {
    static final String ULD_QUEUE = "cargo.uld";
    private final RabbitTemplate rabbit;

    UldPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    String publish(UldRequest request) {
        String messageId =
                request.messageId() != null ? request.messageId() : UUID.randomUUID().toString();
        UldMessage message = UldMessage.from(messageId, request);

        // the default exchange routes straight to the queue named by the routing key
        rabbit.convertAndSend(
                "",
                ULD_QUEUE,
                message,
                amqpMessage -> {
                    amqpMessage.getMessageProperties().setMessageId(messageId);
                    amqpMessage
                            .getMessageProperties()
                            .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return amqpMessage;
                });
        log.info(
                "Published ULD message {} to {}: ULD {} on {} {} {} as day {}",
                messageId,
                ULD_QUEUE,
                request.uldCode(),
                request.flightNumber(),
                request.scheduledDepartureDate(),
                request.departureAirportCode(),
                message.scheduledDepartureDay());
        return messageId;
    }
}
