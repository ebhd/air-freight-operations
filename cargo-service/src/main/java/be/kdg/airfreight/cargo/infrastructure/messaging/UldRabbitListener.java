package be.kdg.airfreight.cargo.infrastructure.messaging;

import be.kdg.airfreight.cargo.application.BookUldCommand;
import be.kdg.airfreight.cargo.application.BookUldService;
import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.BookingRejectedException;
import be.kdg.airfreight.cargo.domain.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.UldCode;
import be.kdg.airfreight.cargo.domain.Weight;
import be.kdg.airfreight.cargo.domain.WeightUnit;
import be.kdg.airfreight.cargo.infrastructure.persistence.MessageReceipts;
import be.kdg.airfreight.cargo.infrastructure.persistence.RejectedMessageStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Receives ULD bookings. Unreadable or invalid messages and bookings that may not be applied are
 * stored as rejected and acknowledged; a rejected message ID stays rejected on redelivery. Database
 * and Flight Operations outages are thrown so the message is retried later.
 *
 * <p>Value objects report missing fields with {@link NullPointerException}, so it is handled as
 * invalid input. Any other unexpected exception is logged as an error and the message is kept in
 * the rejected messages instead of being retried endlessly.
 */
@Component
class UldRabbitListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(UldRabbitListener.class);
    private static final int MAX_MESSAGE_ID_LENGTH = 255;

    private final BookUldService bookUldService;
    private final RejectedMessageStore rejectedMessageStore;
    private final MessageReceipts messageReceipts;
    private final JsonMapper jsonMapper;

    UldRabbitListener(
            BookUldService bookUldService,
            RejectedMessageStore rejectedMessageStore,
            MessageReceipts messageReceipts,
            JsonMapper jsonMapper) {
        this.bookUldService = bookUldService;
        this.rejectedMessageStore = rejectedMessageStore;
        this.messageReceipts = messageReceipts;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = RabbitConfiguration.ULD_QUEUE)
    void receive(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String messageId = message.getMessageProperties().getMessageId();
        LOGGER.debug("Received ULD message {}: {}", messageId, payload);

        UldMessage uldMessage = parse(payload);
        if (uldMessage == null) {
            rejectedMessageStore.store(messageId, payload, "Unreadable JSON or empty message");
            return;
        }
        if (messageId == null) {
            messageId = uldMessage.messageId();
        }
        if (messageId == null
                || messageId.isBlank()
                || messageId.length() > MAX_MESSAGE_ID_LENGTH) {
            rejectedMessageStore.store(
                    messageId, payload, "Message ID must be 1 to 255 characters");
            return;
        }
        if (rejectedMessageStore.contains(messageId)) {
            LOGGER.info("Ignored redelivery of rejected ULD message {}", messageId);
            return;
        }
        Instant firstReceivedAt = messageReceipts.firstReceivedAt(messageId, Instant.now());

        try {
            bookUldService.bookUld(toCommand(messageId, uldMessage, firstReceivedAt));
        } catch (FlightLookupUnavailableException
                | DataAccessException
                | TransactionException outage) {
            throw outage;
        } catch (BookingRejectedException
                | IllegalArgumentException
                | NullPointerException invalid) {
            rejectedMessageStore.store(messageId, payload, invalid.getMessage());
        } catch (RuntimeException unexpected) {
            LOGGER.error("Unexpected error processing ULD message {}", messageId, unexpected);
            rejectedMessageStore.store(
                    messageId, payload, "Unexpected processing error: " + unexpected);
        }
    }

    /** Returns null for unreadable JSON and for a JSON {@code null} body. */
    private UldMessage parse(String payload) {
        try {
            return jsonMapper.readValue(payload, UldMessage.class);
        } catch (JacksonException unreadable) {
            LOGGER.debug("Unreadable ULD message: {}", unreadable.getOriginalMessage());
            return null;
        }
    }

    private static BookUldCommand toCommand(
            String messageId, UldMessage message, Instant firstReceivedAt) {
        Objects.requireNonNull(
                message.scheduledDepartureDay(), "Scheduled departure day is required");
        Objects.requireNonNull(message.weight(), "Weight is required");
        return new BookUldCommand(
                messageId,
                message.flightNumber(),
                message.scheduledDepartureDay(),
                new AirportCode(message.departureAirportCode()),
                new UldCode(message.uldCode()),
                new Weight(message.weight().value(), WeightUnit.parse(message.weight().unit())),
                new AirportCode(message.destination()),
                firstReceivedAt);
    }
}
