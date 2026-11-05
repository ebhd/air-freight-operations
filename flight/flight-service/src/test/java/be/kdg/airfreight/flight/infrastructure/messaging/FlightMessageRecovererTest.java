package be.kdg.airfreight.flight.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.flight.infrastructure.persistence.RejectedMessageStore;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.CannotCreateTransactionException;
import tools.jackson.databind.json.JsonMapper;

class FlightMessageRecovererTest {
    private final FlightMessageRecoverer recoverer =
            new FlightMessageRecoverer(
                    Mockito.mock(RejectedMessageStore.class), JsonMapper.builder().build());

    @ParameterizedTest
    @MethodSource("temporaryDatabaseFailures")
    void databaseFailureRequeuesOriginalDelivery(Throwable failure) {
        Message message = message();

        assertThatThrownBy(() -> recoverer.recover(message, failure))
                .isInstanceOf(ImmediateRequeueAmqpException.class)
                .hasCause(failure);

        assertOriginalMessage(message);
    }

    @ParameterizedTest
    @MethodSource("temporaryDatabaseFailures")
    void databaseFailureInsideListenerExceptionStillRequeues(Throwable failure) {
        Message message = message();
        var wrapped = new ListenerExecutionFailedException("Listener failed", failure, message);

        assertThatThrownBy(() -> recoverer.recover(message, wrapped))
                .isInstanceOf(ImmediateRequeueAmqpException.class)
                .hasCause(wrapped);
    }

    @ParameterizedTest
    @MethodSource("permanentFailures")
    void invalidMessageOrPermanentFailureIsRejectedWithoutRequeue(Throwable failure) {
        Message message = message();

        assertThatThrownBy(() -> recoverer.recover(message, failure))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasRootCause(failure);

        assertOriginalMessage(message);
    }

    @Test
    void requeueDoesNotRequireASourceQueueMapping() {
        Message message = message();
        message.getMessageProperties().setConsumerQueue(null);

        assertThatThrownBy(
                        () ->
                                recoverer.recover(
                                        message,
                                        new DataAccessResourceFailureException("Database offline")))
                .isInstanceOf(ImmediateRequeueAmqpException.class);
    }

    private static Stream<Throwable> temporaryDatabaseFailures() {
        return Stream.of(
                new TransientDataAccessResourceException("Connection interrupted"),
                new RecoverableDataAccessException("Connection needs reopening"),
                new DataAccessResourceFailureException("Database offline"),
                new CannotCreateTransactionException("Cannot open transaction"),
                new SQLException("Connection lost", "08006"),
                new IllegalStateException(
                        "Persistence failure", new SQLException("Cannot connect", "08001")));
    }

    private static Stream<Throwable> permanentFailures() {
        return Stream.of(
                new IllegalArgumentException("Invalid message"),
                new DataIntegrityViolationException("Invalid row"),
                new InvalidDataAccessResourceUsageException("Missing table"),
                new SQLException("Invalid SQL", "42000"),
                new SQLException("Missing SQL state"),
                new IllegalStateException("Unexpected processing failure"));
    }

    private static Message message() {
        var properties = new MessageProperties();
        properties.setMessageId("message-1");
        properties.setConsumerQueue("flight.ssm");
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setHeader("formatVersion", 1);
        return new Message("original payload".getBytes(StandardCharsets.UTF_8), properties);
    }

    private static void assertOriginalMessage(Message message) {
        assertThat(message.getBody())
                .isEqualTo("original payload".getBytes(StandardCharsets.UTF_8));
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo("message-1");
        assertThat(message.getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getHeaders())
                .containsExactlyEntriesOf(Map.of("formatVersion", 1));
    }
}
