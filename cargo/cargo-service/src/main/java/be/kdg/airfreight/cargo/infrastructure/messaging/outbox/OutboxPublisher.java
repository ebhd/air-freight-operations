package be.kdg.airfreight.cargo.infrastructure.messaging.outbox;

import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaOutboxMessage;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaOutboxMessageRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// same pattern as flight: a row is published only after rabbitmq confirms it
@Slf4j
@Component
public class OutboxPublisher {
    private static final int BATCH_SIZE = 50;
    private static final long CONFIRMATION_TIMEOUT_SECONDS = 5;

    private final JpaOutboxMessageRepository repository;
    private final RabbitTemplate rabbit;
    private final Clock clock;

    public OutboxPublisher(
            JpaOutboxMessageRepository repository, RabbitTemplate rabbit, Clock clock) {
        this.repository = repository;
        this.rabbit = rabbit;
        this.clock = clock;
    }

    // right after the commit so the event normally leaves at once
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public synchronized void onCommitted(OutboxMessage message) {
        // after the commit, so the action and this message are saved together
        log.info("Outbox message {} ({}) stored", message.id(), message.routingKey());
        try {
            repository
                    .findById(message.id())
                    .map(JpaOutboxMessage::toDomain)
                    .filter(pending -> pending.publishedAt() == null)
                    .ifPresent(this::tryPublish);
        } catch (Exception exception) {
            log.warn(
                    "Could not load outbox message {}; scheduled retry will recover it",
                    message.id(),
                    exception);
        }
    }

    // picks up what failed before, also after a restart
    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public synchronized void publishPending() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                List<JpaOutboxMessage> pending =
                        repository.findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(
                                PageRequest.of(0, BATCH_SIZE));
                for (JpaOutboxMessage entity : pending) {
                    // keep the order: stop at the first failure and try again later
                    if (!tryPublish(entity.toDomain())) {
                        return;
                    }
                }
                if (pending.size() < BATCH_SIZE) {
                    return;
                }
            }
        } catch (Exception exception) {
            log.warn("Could not read pending outbox messages; will retry", exception);
        }
    }

    // a crash between the ack and this save sends the same id again so consumers deduplicate
    private boolean tryPublish(OutboxMessage message) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        try {
            sendAndConfirm(message);
            repository.save(JpaOutboxMessage.fromDomain(message.publish(Instant.now(clock))));
            log.info(
                    "Outbox message {} ({}) delivered to RabbitMQ",
                    message.id(),
                    message.routingKey());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (DataAccessException | TransactionException exception) {
            // rabbitmq has it, but the row stays pending so consumers get it again and ignore it
            log.error(
                    "Outbox message {} ({}) delivered to RabbitMQ but not marked as published;"
                            + " it will be sent again",
                    message.id(),
                    message.routingKey(),
                    exception);
            return false;
        } catch (Exception exception) {
            log.warn(
                    "Outbox message {} ({}) waiting for sending; will retry",
                    message.id(),
                    message.routingKey(),
                    exception);
            return false;
        }
    }

    private void sendAndConfirm(OutboxMessage message)
            throws InterruptedException, ExecutionException, TimeoutException {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(message.id().toString());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message amqpMessage =
                new Message(message.payload().getBytes(StandardCharsets.UTF_8), properties);

        CorrelationData correlation = new CorrelationData();
        rabbit.send(message.exchange(), message.routingKey(), amqpMessage, correlation);

        CorrelationData.Confirm confirmation =
                correlation.getFuture().get(CONFIRMATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!confirmation.ack()) {
            throw new IllegalStateException(
                    "RabbitMQ rejected outbox message "
                            + message.id()
                            + ": "
                            + confirmation.reason());
        }
        // mandatory publishing returns a message that reached no queue
        if (correlation.getReturned() != null) {
            throw new IllegalStateException(
                    "RabbitMQ could not route outbox message "
                            + message.id()
                            + ": "
                            + Objects.requireNonNull(correlation.getReturned()).getReplyText());
        }
    }
}
