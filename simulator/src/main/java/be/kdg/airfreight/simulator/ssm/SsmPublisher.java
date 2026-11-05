package be.kdg.airfreight.simulator.ssm;

import java.util.UUID;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

// public so the replanning listener can send its ssm the same way as the http endpoint
@Component
public class SsmPublisher {
    static final String SSM_QUEUE = "flight.ssm";
    private final RabbitTemplate rabbit;

    SsmPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    String publish(SsmRequest request) {
        String messageId = UUID.randomUUID().toString();
        SsmMessage message =
                new SsmMessage(
                        messageId,
                        request.startDate(),
                        request.endDate(),
                        request.operatingDays(),
                        request.departureTime(),
                        request.arrivalTime(),
                        request.flightNumber(),
                        request.departureAirportCode(),
                        request.arrivalAirportCode(),
                        request.aircraftRegistration());
        return publish(message);
    }

    // the message id goes in the body and in the amqp properties
    public String publish(SsmMessage message) {
        String messageId = message.messageId();
        rabbit.convertAndSend(
                "",
                SSM_QUEUE,
                message,
                amqpMessage -> {
                    amqpMessage.getMessageProperties().setMessageId(messageId);
                    amqpMessage
                            .getMessageProperties()
                            .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return amqpMessage;
                });
        return messageId;
    }
}
