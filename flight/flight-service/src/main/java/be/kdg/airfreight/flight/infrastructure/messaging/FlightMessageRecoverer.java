package be.kdg.airfreight.flight.infrastructure.messaging;

import be.kdg.airfreight.flight.infrastructure.persistence.RejectedMessageStore;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.ImmediateRequeueMessageRecoverer;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@Slf4j
class FlightMessageRecoverer implements MessageRecoverer {
    private final MessageRecoverer requeueRecoverer = new ImmediateRequeueMessageRecoverer();
    private final MessageRecoverer deadLetterRecoverer = new RejectAndDontRequeueRecoverer();
    private final RejectedMessageStore rejectedMessages;
    private final JsonMapper jsonMapper;

    FlightMessageRecoverer(RejectedMessageStore rejectedMessages, JsonMapper jsonMapper) {
        this.rejectedMessages = rejectedMessages;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void recover(@NonNull Message message, @NonNull Throwable cause) {
        if (isTemporaryDatabaseFailure(cause)) {
            // an error because nothing was saved, the message itself is safe on the queue
            log.error(
                    "Requeuing message {} on queue {} after a temporary database failure",
                    message.getMessageProperties().getMessageId(),
                    message.getMessageProperties().getConsumerQueue(),
                    cause);
            requeueRecoverer.recover(message, cause);
        } else {
            if (isDatabaseFailure(cause)) {
                log.error(
                        "Rejecting message {} on queue {} after a database failure that retries"
                                + " did not fix",
                        message.getMessageProperties().getMessageId(),
                        message.getMessageProperties().getConsumerQueue(),
                        cause);
            }
            storeRejection(message, cause);
            deadLetterRecoverer.recover(message, cause);
        }
    }

    // the reason is stored here because the dead letter queue only keeps the message
    private void storeRejection(Message message, Throwable cause) {
        String messageId = messageId(message);
        try {
            rejectedMessages.store(
                    messageId,
                    message.getBody(),
                    NestedExceptionUtils.getMostSpecificCause(cause).toString());
        } catch (RuntimeException storeFailure) {
            log.error("Could not store rejected message {}", messageId, storeFailure);
            throw storeFailure;
        }
    }

    // the amqp property comes first, a sender may also have the id only in the json body
    private String messageId(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        try {
            String bodyId = jsonMapper.readTree(message.getBody()).path("messageId").asString("");
            return bodyId.isBlank() ? null : bodyId;
        } catch (JacksonException unreadableBody) {
            return null; // the content and reason are still stored, only without an id
        }
    }

    private boolean isDatabaseFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessException || cause instanceof TransactionException) {
                return true;
            }
        }
        return false;
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
