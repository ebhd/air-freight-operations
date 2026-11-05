package be.kdg.airfreight.formatter.infrastructure.messaging.prepared;

import be.kdg.airfreight.formatter.domain.MessageContent;
import be.kdg.airfreight.formatter.domain.MessagePublisher;
import be.kdg.airfreight.formatter.infrastructure.messaging.RabbitConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@Slf4j
public class PreparedMessagePublisher implements MessagePublisher {
    private final RabbitTemplate rabbit;
    private final JsonMapper json;

    public PreparedMessagePublisher(RabbitTemplate rabbit, JsonMapper json) {
        this.rabbit = rabbit;
        this.json = json;
    }

    @Override
    public void publish(UUID sourceMessageId, List<MessageContent> contents) {
        var batch =
                new PartnerMessagesPreparedMessage(
                        PartnerMessagesPreparedMessage.VERSION,
                        sourceMessageId,
                        contents.stream()
                                .map(
                                        content ->
                                                new PartnerMessagesPreparedMessage.PreparedContent(
                                                        content.type(),
                                                        content.formatVersion(),
                                                        content.payload()))
                                .toList());
        var properties = new MessageProperties();
        properties.setMessageId(sourceMessageId.toString());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        var correlation = new CorrelationData();
        try {
            rabbit.send(
                    RabbitConfiguration.FORMATTER_EVENTS,
                    RabbitConfiguration.PREPARED,
                    new Message(json.writeValueAsBytes(batch), properties),
                    correlation);
            var confirmation = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirmation.ack() || correlation.getReturned() != null) {
                throw new AmqpException(
                        "Prepared messages were not confirmed and routed: " + sourceMessageId);
            }
            log.info(
                    "Delivered prepared batch {} to RabbitMQ ({} messages)",
                    sourceMessageId,
                    contents.size());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted publishing prepared messages", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AmqpException("Could not confirm prepared messages", exception);
        }
    }
}
