package be.kdg.airfreight.cargo.infrastructure.messaging.uld;

import be.kdg.airfreight.cargo.application.booking.BookUldCommand;
import be.kdg.airfreight.cargo.application.booking.BookUldService;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import be.kdg.airfreight.cargo.domain.cargoplan.WeightUnit;
import be.kdg.airfreight.cargo.domain.exception.BookingRejectedException;
import be.kdg.airfreight.cargo.domain.exception.FlightLookupRejectedException;
import be.kdg.airfreight.cargo.domain.exception.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.infrastructure.messaging.config.RabbitConfiguration;
import be.kdg.airfreight.cargo.infrastructure.persistence.MessageReceipts;
import be.kdg.airfreight.cargo.infrastructure.persistence.RejectedMessageStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@Slf4j
class UldRabbitListener {
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
        String amqpMessageId = message.getMessageProperties().getMessageId();
        log.debug("Received ULD message {}: {}", amqpMessageId, payload);

        UldMessage uldMessage = parse(payload);
        if (uldMessage == null) {
            rejectedMessageStore.store(amqpMessageId, payload, "Unreadable JSON or empty message");
            return;
        }
        String messageId = amqpMessageId != null ? amqpMessageId : uldMessage.messageId();
        if (!isValidMessageId(messageId)) {
            rejectedMessageStore.store(
                    messageId, payload, "Message ID must be 1 to 255 characters");
            return;
        }
        if (rejectedMessageStore.contains(messageId)) {
            log.info("Ignored redelivery of rejected ULD message {}", messageId);
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
        book(messageId, payload, uldMessage);
    }

    private static boolean isValidMessageId(String messageId) {
        return messageId != null
                && !messageId.isBlank()
                && messageId.length() <= MAX_MESSAGE_ID_LENGTH;
    }

    private void book(String messageId, String payload, UldMessage uldMessage) {
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
                | InvalidCargoValueException invalid) {
            // the reason column can't be null and not every exception has a message
            String reason =
                    Objects.requireNonNullElse(
                            invalid.getMessage(), invalid.getClass().getSimpleName());
            rejectedMessageStore.store(messageId, payload, reason);
        } catch (RuntimeException unexpected) {
            // a bug so reject instead of retrying forever
            log.error("Unexpected error processing ULD message {}", messageId, unexpected);
            rejectedMessageStore.store(
                    messageId, payload, "Unexpected processing error: " + unexpected);
        }
    }

    // null for unreadable json or a json null body
    private UldMessage parse(String payload) {
        try {
            return jsonMapper.readValue(payload, UldMessage.class);
        } catch (JacksonException unreadable) {
            log.debug("Unreadable ULD message: {}", unreadable.getOriginalMessage());
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
            throw new InvalidCargoValueException(field + " is required");
        }
        return value;
    }
}
