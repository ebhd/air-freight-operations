package be.kdg.airfreight.formatter.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.retry.ImmediateRequeueMessageRecoverer;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class FormatterMessageRecoverer implements MessageRecoverer {
    private final MessageRecoverer requeue = new ImmediateRequeueMessageRecoverer();
    private final MessageRecoverer reject = new RejectAndDontRequeueRecoverer();

    @Override
    public void recover(Message message, Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof AmqpException
                    && !(cause instanceof ListenerExecutionFailedException)
                    && !(cause instanceof MessageConversionException)) {
                log.warn(
                        "Requeuing source {} after prepared-message publication failure",
                        message.getMessageProperties().getMessageId(),
                        failure);
                requeue.recover(message, failure);
                return;
            }
        }
        log.warn(
                "Rejected message {} on queue {}: {}",
                message.getMessageProperties().getMessageId(),
                message.getMessageProperties().getConsumerQueue(),
                NestedExceptionUtils.getMostSpecificCause(failure).toString());
        reject.recover(message, failure);
    }
}
