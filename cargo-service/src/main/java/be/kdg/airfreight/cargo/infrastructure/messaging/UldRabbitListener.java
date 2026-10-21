package be.kdg.airfreight.cargo.infrastructure.messaging;

import be.kdg.airfreight.cargo.application.BookUldCommand;
import be.kdg.airfreight.cargo.application.BookUldService;
import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.BookingRejectedException;
import be.kdg.airfreight.cargo.domain.FlightLookupRejectedException;
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

@Component
class UldRabbitListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(UldRabbitListener.class);
    private static final int MAX_MESSAGE_ID_LENGTH = 255;
    private static final int SUPPORTED_VERSION = 1;

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
        // a missing version counts as unknown so we never guess the field layout
        if (!Objects.equals(uldMessage.version(), SUPPORTED_VERSION)) {
            rejectedMessageStore.store(
                    messageId,
                    payload,
                    "Message version must be "
                            + SUPPORTED_VERSION
                            + ", was "
                            + uldMessage.version());
            return;
        }
        Instant firstReceivedAt = messageReceipts.firstReceivedAt(messageId, Instant.now());

        try {
            bookUldService.bookUld(toCommand(messageId, uldMessage, firstReceivedAt));
        } catch (FlightLookupUnavailableException
                | DataAccessException
                | TransactionException outage) {
            // outage so throw and let rabbit retry
            throw outage;
        } catch (BookingRejectedException
                | FlightLookupRejectedException
                | IllegalArgumentException invalid) {
            // the reason column cant be null and not every exception has a message
            String reason =
                    Objects.requireNonNullElse(
                            invalid.getMessage(), invalid.getClass().getSimpleName());
            rejectedMessageStore.store(messageId, payload, reason);
        } catch (RuntimeException unexpected) {
            // a bug so reject instead of retrying forever
            LOGGER.error("Unexpected error processing ULD message {}", messageId, unexpected);
            rejectedMessageStore.store(
                    messageId, payload, "Unexpected processing error: " + unexpected);
        }
    }

    // null for unreadable json or a json null body
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
        UldMessage.WeightValue weight = required(message.weight(), "Weight");
        return new BookUldCommand(
                messageId,
                required(message.flightNumber(), "Flight number"),
                required(message.scheduledDepartureDay(), "Scheduled departure day"),
                new AirportCode(required(message.departureAirportCode(), "Departure airport code")),
                new UldCode(required(message.uldCode(), "ULD code")),
                new Weight(
                        required(weight.value(), "Weight value"), WeightUnit.parse(weight.unit())),
                new AirportCode(required(message.destination(), "Destination")),
                firstReceivedAt);
    }

    // a missing json field is a rejection not a NullPointerException
    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
