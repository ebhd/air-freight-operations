package be.kdg.airfreight.partner.infrastructure.messaging;

import be.kdg.airfreight.partner.domain.PartnerMessage;
import be.kdg.airfreight.partner.domain.PartnerMessageRepository;
import be.kdg.airfreight.partner.domain.PublicationState;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

@Component
@Slf4j
public class PartnerMessagePublisher {
    private static final int BATCH_SIZE = 50;
    private static final long CONFIRMATION_TIMEOUT_SECONDS = 5;

    private final PartnerMessageRepository repository;
    private final RabbitTemplate rabbit;
    private final JsonMapper jsonMapper;

    public PartnerMessagePublisher(
            PartnerMessageRepository repository, RabbitTemplate rabbit, JsonMapper jsonMapper) {
        this.repository = repository;
        this.rabbit = rabbit;
        this.jsonMapper = jsonMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public synchronized void onCommitted(PartnerMessage message) {
        try {
            repository
                    .findById(message.id())
                    .filter(pending -> pending.state() == PublicationState.PENDING)
                    .ifPresent(this::tryPublish);
        } catch (Exception exception) {
            log.warn(
                    "Could not load partner message {}; scheduled retry will recover it",
                    message.id().value(),
                    exception);
        }
    }

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public synchronized void publishPending() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                var pending = repository.findPending(BATCH_SIZE);
                for (var message : pending) {
                    if (!tryPublish(message)) {
                        return;
                    }
                }
                if (pending.size() < BATCH_SIZE) {
                    return;
                }
            }
        } catch (Exception exception) {
            log.warn("Could not read pending partner messages; will retry", exception);
        }
    }

    private boolean tryPublish(PartnerMessage message) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        try {
            sendAndConfirm(message);
            message.markPublished();
            repository.save(message);
            log.info(
                    "Published partner message {}, {}",
                    message.id().value(),
                    message.content().type());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception exception) {
            log.warn(
                    "Partner message {} remains pending will retry",
                    message.id().value(),
                    exception);
            return false;
        }
    }

    private void sendAndConfirm(PartnerMessage message)
            throws InterruptedException, ExecutionException, TimeoutException {

        // remove when adding cpm etc
        if (!"FLM".equals(message.content().type())) {
            throw new IllegalArgumentException(
                    "Unsupported partner message type: " + message.content().type());
        }

        var envelope =
                new PartnerMessageEnvelope(
                        message.id().value(),
                        message.createdAtUTC(),
                        message.content().formatVersion(),
                        message.content().type(),
                        jsonMapper.readTree(message.content().payload()));

        var properties = new MessageProperties();
        properties.setMessageId(message.id().value().toString());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        var amqpMessage = new Message(jsonMapper.writeValueAsBytes(envelope), properties);
        var correlation = new CorrelationData();

        rabbit.send(
                RabbitConfiguration.PARTNER_MESSAGES,
                RabbitConfiguration.FLM,
                amqpMessage,
                correlation);
        var confirmation =
                correlation.getFuture().get(CONFIRMATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!confirmation.ack()) {
            throw new IllegalStateException(
                    "RabbitMQ rejected partner message "
                            + message.id().value()
                            + ": "
                            + confirmation.reason());
        }
        var returned = correlation.getReturned();
        if (returned != null) {
            throw new IllegalStateException(
                    "RabbitMQ could not route partner message "
                            + message.id().value()
                            + ": "
                            + returned.getReplyText());
        }
    }
}
