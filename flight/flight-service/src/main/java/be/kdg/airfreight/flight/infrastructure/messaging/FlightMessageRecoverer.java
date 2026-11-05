package be.kdg.airfreight.flight.infrastructure.messaging;

import be.kdg.airfreight.flight.infrastructure.persistence.RejectedMessageStore;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.ImmediateRequeueMessageRecoverer;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

@Component
@Slf4j
class FlightMessageRecoverer implements MessageRecoverer {
    private final MessageRecoverer requeueRecoverer = new ImmediateRequeueMessageRecoverer();
    private final MessageRecoverer deadLetterRecoverer = new RejectAndDontRequeueRecoverer();
    private final RejectedMessageStore rejectedMessages;

    FlightMessageRecoverer(RejectedMessageStore rejectedMessages) {
        this.rejectedMessages = rejectedMessages;
    }

    @Override
    public void recover(@NonNull Message message, @NonNull Throwable cause) {
        if (isTemporaryDatabaseFailure(cause)) {
            log.warn(
                    "Requeuing message {} on queue {} after a temporary database failure",
                    message.getMessageProperties().getMessageId(),
                    message.getMessageProperties().getConsumerQueue(),
                    cause);
            requeueRecoverer.recover(message, cause);
        } else {
            // the reason is stored here because the dead letter queue only keeps the message
            rejectedMessages.store(
                    message.getMessageProperties().getMessageId(),
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    NestedExceptionUtils.getMostSpecificCause(cause).toString());
            deadLetterRecoverer.recover(message, cause);
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
