package be.kdg.airfreight.cargo.infrastructure.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaOutboxMessage;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaOutboxMessageRepository;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;

class OutboxPublisherTest {
    private static final Instant NOW = Instant.parse("2026-10-22T06:00:05Z");
    private static final OutboxMessage PENDING =
            new OutboxMessage(
                    UUID.fromString("6f1c2d3e-0000-4000-8000-000000000001"),
                    "cargo.events",
                    "cargo.load-finalized",
                    "{\"version\":1}",
                    Instant.parse("2026-10-22T06:00:00Z"),
                    null);

    private final JpaOutboxMessageRepository repository = mock(JpaOutboxMessageRepository.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final OutboxPublisher publisher =
            new OutboxPublisher(repository, rabbit, Clock.fixed(NOW, ZoneOffset.UTC));
    // the message id of every send attempt
    private final List<String> sentIds = new ArrayList<>();

    @Test
    void confirmedMessageIsMarkedPublished() {
        givenStored(PENDING);
        brokerAnswers(new CorrelationData.Confirm(true, null), null);

        publisher.onCommitted(PENDING);

        ArgumentCaptor<JpaOutboxMessage> saved = ArgumentCaptor.forClass(JpaOutboxMessage.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().toDomain().publishedAt()).isEqualTo(NOW);
        assertThat(sentIds).containsExactly(PENDING.id().toString());
    }

    @Test
    void sendsAPersistentJsonMessageWithTheRowIdAsMessageId() {
        givenStored(PENDING);
        brokerAnswers(new CorrelationData.Confirm(true, null), null);

        publisher.onCommitted(PENDING);

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbit)
                .send(
                        eq("cargo.events"),
                        eq("cargo.load-finalized"),
                        sent.capture(),
                        any(CorrelationData.class));
        MessageProperties properties = sent.getValue().getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(PENDING.id().toString());
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(new String(sent.getValue().getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"version\":1}");
    }

    @Test
    void rejectedMessageStaysPendingAndTheRetryUsesTheSameId() {
        givenStored(PENDING);
        brokerAnswers(new CorrelationData.Confirm(false, "nack"), null);
        publisher.onCommitted(PENDING);
        verify(repository, never()).save(any());

        when(repository.findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(any(Pageable.class)))
                .thenReturn(List.of(JpaOutboxMessage.fromDomain(PENDING)));
        brokerAnswers(new CorrelationData.Confirm(true, null), null);
        publisher.publishPending();

        verify(repository, times(1)).save(any());
        assertThat(sentIds).containsExactly(PENDING.id().toString(), PENDING.id().toString());
    }

    // without a bound queue mandatory publishing returns the message so it is not lost
    @Test
    void unroutableMessageStaysPending() {
        givenStored(PENDING);
        ReturnedMessage returned =
                new ReturnedMessage(
                        new Message(new byte[0]),
                        312,
                        "NO_ROUTE",
                        "cargo.events",
                        "cargo.load-finalized");
        brokerAnswers(new CorrelationData.Confirm(true, null), returned);

        publisher.onCommitted(PENDING);

        verify(repository, never()).save(any());
    }

    @Test
    void brokerDownStaysPending() {
        givenStored(PENDING);
        doAnswer(
                        call -> {
                            throw new AmqpConnectException(new ConnectException("refused"));
                        })
                .when(rabbit)
                .send(
                        any(String.class),
                        any(String.class),
                        any(Message.class),
                        any(CorrelationData.class));

        publisher.onCommitted(PENDING);

        verify(repository, never()).save(any());
    }

    @Test
    void alreadyPublishedRowIsNotSentAgain() {
        givenStored(PENDING.publish(NOW));

        publisher.onCommitted(PENDING);

        verify(rabbit, never())
                .send(
                        any(String.class),
                        any(String.class),
                        any(Message.class),
                        any(CorrelationData.class));
    }

    @Test
    void scheduledRunStopsAtTheFirstFailureToKeepTheOrder() {
        OutboxMessage second =
                new OutboxMessage(
                        UUID.fromString("6f1c2d3e-0000-4000-8000-000000000002"),
                        "cargo.events",
                        "cargo.load-finalized",
                        "{}",
                        Instant.parse("2026-10-22T06:01:00Z"),
                        null);
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAscIdAsc(any(Pageable.class)))
                .thenReturn(
                        List.of(
                                JpaOutboxMessage.fromDomain(PENDING),
                                JpaOutboxMessage.fromDomain(second)));
        brokerAnswers(new CorrelationData.Confirm(false, "nack"), null);

        publisher.publishPending();

        assertThat(sentIds).containsExactly(PENDING.id().toString());
        verify(repository, never()).save(any());
    }

    private void givenStored(OutboxMessage message) {
        when(repository.findById(PENDING.id()))
                .thenReturn(Optional.of(JpaOutboxMessage.fromDomain(message)));
    }

    // completes the confirm future like the broker would
    private void brokerAnswers(CorrelationData.Confirm confirm, ReturnedMessage returned) {
        doAnswer(
                        call -> {
                            Message message = call.getArgument(2);
                            sentIds.add(message.getMessageProperties().getMessageId());
                            CorrelationData correlation = call.getArgument(3);
                            correlation.setReturned(returned);
                            correlation.getFuture().complete(confirm);
                            return null;
                        })
                .when(rabbit)
                .send(
                        any(String.class),
                        any(String.class),
                        any(Message.class),
                        any(CorrelationData.class));
    }
}
