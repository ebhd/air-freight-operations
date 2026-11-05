package be.kdg.airfreight.formatter.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;

class FormatterMessageRecovererTest {
    private final FormatterMessageRecoverer recoverer = new FormatterMessageRecoverer();
    private final Message message = new Message(new byte[0], new MessageProperties());

    @Test
    void publicationFailureRequeuesTheSource() {
        var failure = new AmqpException("Broker unavailable");
        assertThatThrownBy(() -> recoverer.recover(message, failure))
                .isInstanceOf(ImmediateRequeueAmqpException.class)
                .hasCause(failure);
    }

    @Test
    void wrappedPublicationFailureAlsoRequeuesTheSource() {
        var failure =
                new ListenerExecutionFailedException(
                        "Listener failed", new AmqpException("Broker unavailable"), message);
        assertThatThrownBy(() -> recoverer.recover(message, failure))
                .isInstanceOf(ImmediateRequeueAmqpException.class)
                .hasCause(failure);
    }

    @Test
    void invalidSourceIsRejectedWithoutRequeue() {
        var failure =
                new ListenerExecutionFailedException(
                        "Listener failed",
                        new IllegalArgumentException("Unsupported version"),
                        message);
        assertThatThrownBy(() -> recoverer.recover(message, failure))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasRootCause(failure.getCause());
    }

    @Test
    void malformedJsonIsRejectedWithoutRequeue() {
        var failure = new MessageConversionException("Invalid JSON");
        assertThatThrownBy(() -> recoverer.recover(message, failure))
                .isInstanceOf(ListenerExecutionFailedException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasRootCause(failure);
    }
}
