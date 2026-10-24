package be.kdg.airfreight.flight.infrastructure.messaging;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecovererWithConfirms;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

@Component
@Slf4j
class FlightMessageRecoverer implements MessageRecoverer {
    private final Map<String, RepublishMessageRecovererWithConfirms> retryPublishers;
    private final RejectAndDontRequeueRecoverer deadLetterRecoverer =
            new RejectAndDontRequeueRecoverer();

    FlightMessageRecoverer(RabbitTemplate rabbit) {
        var publishers = new HashMap<String, RepublishMessageRecovererWithConfirms>();
        publishers.put(
                RabbitConfiguration.SSM_QUEUE,
                createRetryPublisher(rabbit, RabbitConfiguration.SSM_RETRY_QUEUE));
        retryPublishers = Map.copyOf(publishers);
    }

    private RepublishMessageRecovererWithConfirms createRetryPublisher(
            RabbitTemplate rabbit, String retryQueue) {
        var publisher =
                new RepublishMessageRecovererWithConfirms(
                        rabbit, "", retryQueue, ConfirmType.CORRELATED);
        publisher.setConfirmTimeout(5_000);
        return publisher;
    }

    @Override
    public void recover(@NonNull Message message, @NonNull Throwable cause) {
        if (!isTemporaryDatabaseFailure(cause)) {
            deadLetterRecoverer.recover(message, cause);
            return;
        }

        String sourceQueue = message.getMessageProperties().getConsumerQueue();
        var retryPublisher = sourceQueue == null ? null : retryPublishers.get(sourceQueue);
        if (retryPublisher == null) {
            throw new ImmediateRequeueAmqpException(
                    "No database retry route configured for source queue: " + sourceQueue, cause);
        }

        try {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            retryPublisher.recover(message, cause);
            log.warn(
                    "Deferred message {} from queue {} for a database retry",
                    message.getMessageProperties().getMessageId(),
                    sourceQueue);
        } catch (RuntimeException failure) {
            throw new ImmediateRequeueAmqpException(
                    "Could not safely store message for retry", failure);
        }
    }

    private boolean isTemporaryDatabaseFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TransientDataAccessException
                    || cause instanceof RecoverableDataAccessException
                    || cause instanceof DataAccessResourceFailureException
                    || cause instanceof CannotCreateTransactionException) {
                return true;
            }
            if (cause instanceof SQLException sqlException
                    && sqlException.getSQLState() != null
                    && sqlException.getSQLState().startsWith("08")) {
                return true;
            }
        }
        return false;
    }
}
