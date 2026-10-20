package be.kdg.airfreight.partner.infrastructure.messaging;

import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
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
public class FlightCreatedMessageRecoverer implements MessageRecoverer {
    private final RepublishMessageRecovererWithConfirms retryPublisher;
    private final RejectAndDontRequeueRecoverer deadLetterRecoverer =
            new RejectAndDontRequeueRecoverer();

    public FlightCreatedMessageRecoverer(RabbitTemplate rabbit) {
        retryPublisher =
                new RepublishMessageRecovererWithConfirms(
                        rabbit,
                        "",
                        RabbitConfiguration.FLIGHT_CREATED_RETRY_QUEUE,
                        ConfirmType.CORRELATED);
        retryPublisher.setConfirmTimeout(5_000);
    }

    @Override
    public void recover(Message message, Throwable cause) {
        if (!isTemporaryDatabaseFailure(cause)) {
            deadLetterRecoverer.recover(message, cause);
            return;
        }

        try {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            retryPublisher.recover(message, cause);
            log.warn(
                    "Deferred FlightCreated {} for a database retry in 30 seconds",
                    message.getMessageProperties().getMessageId());
        } catch (RuntimeException failure) {
            throw new ImmediateRequeueAmqpException(
                    "Could not safely store FlightCreated for retry", failure);
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
