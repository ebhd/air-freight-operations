package be.kdg.airfreight.cargo.infrastructure.messaging;

import be.kdg.airfreight.cargo.application.cargoplan.MoveCargoPlanService;
import be.kdg.airfreight.cargo.application.flight.FlightCopyService;
import be.kdg.airfreight.cargo.application.flight.FlightRemovalService;
import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.cargoplan.BookingRejectedException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupRejectedException;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.infrastructure.persistence.RejectedMessageStore;
import be.kdg.airfreight.cargo.infrastructure.persistence.jpa.JpaOutboxMessageRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// flight's events translated to cargo's model, the same reject or retry rules as ULD messages
@Component
@Slf4j
class FlightEventListener {
    private static final int SUPPORTED_VERSION = 1;

    private final FlightCopyService flightCopyService;
    private final MoveCargoPlanService moveCargoPlanService;
    private final FlightRemovalService flightRemovalService;
    private final JpaOutboxMessageRepository outboxRepository;
    private final RejectedMessageStore rejectedMessageStore;
    private final JsonMapper jsonMapper;

    FlightEventListener(
            FlightCopyService flightCopyService,
            MoveCargoPlanService moveCargoPlanService,
            FlightRemovalService flightRemovalService,
            JpaOutboxMessageRepository outboxRepository,
            RejectedMessageStore rejectedMessageStore,
            JsonMapper jsonMapper) {
        this.flightCopyService = flightCopyService;
        this.moveCargoPlanService = moveCargoPlanService;
        this.flightRemovalService = flightRemovalService;
        this.outboxRepository = outboxRepository;
        this.rejectedMessageStore = rejectedMessageStore;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = RabbitConfiguration.FLIGHT_CREATED_QUEUE)
    void receiveCreated(Message message) {
        process(
                message,
                FlightCreatedMessage.class,
                FlightCreatedMessage::version,
                created ->
                        flightCopyService.recordCreated(
                                toFlight(created), required(created.occurredOn(), "Occurred on")));
    }

    @RabbitListener(queues = RabbitConfiguration.FLIGHT_ESTIMATES_CHANGED_QUEUE)
    void receiveEstimates(Message message) {
        process(
                message,
                FlightEstimatesChangedMessage.class,
                FlightEstimatesChangedMessage::version,
                changed ->
                        flightCopyService.recordEstimates(
                                flightKey(
                                        changed.flightNumber(),
                                        changed.scheduledDepartureDate(),
                                        changed.departureAirportCode()),
                                required(changed.estimatedDeparture(), "Estimated departure"),
                                required(changed.estimatedArrival(), "Estimated arrival"),
                                required(changed.occurredOn(), "Occurred on")));
    }

    @RabbitListener(queues = RabbitConfiguration.FLIGHT_DEPARTED_QUEUE)
    void receiveDeparted(Message message) {
        process(
                message,
                FlightKeyMessage.class,
                FlightKeyMessage::version,
                departed -> flightCopyService.recordDeparted(flightKey(departed)));
    }

    @RabbitListener(queues = RabbitConfiguration.FLIGHT_LANDED_QUEUE)
    void receiveLanded(Message message) {
        process(
                message,
                FlightKeyMessage.class,
                FlightKeyMessage::version,
                landed -> flightCopyService.recordLanded(flightKey(landed)));
    }

    @RabbitListener(queues = RabbitConfiguration.FLIGHT_REPLANNED_QUEUE)
    void receiveReplanned(Message message) {
        process(
                message,
                FlightKeyMessage.class,
                FlightKeyMessage::version,
                replanned -> flightCopyService.recordReplanned(flightKey(replanned)));
    }

    @RabbitListener(queues = RabbitConfiguration.FLIGHT_REPLACED_QUEUE)
    void receiveReplaced(Message message) {
        process(
                message,
                FlightReplacedMessage.class,
                FlightReplacedMessage::version,
                replaced ->
                        moveCargoPlanService.move(
                                flightKey(
                                        replaced.flightNumber(),
                                        replaced.scheduledDepartureDate(),
                                        replaced.departureAirportCode()),
                                flightKey(
                                        replaced.newFlightNumber(),
                                        replaced.newScheduledDepartureDate(),
                                        replaced.newDepartureAirportCode())));
    }

    @RabbitListener(queues = FlightRemovedConfiguration.FLIGHT_REMOVED_QUEUE)
    void receiveRemoved(Message message) {
        process(
                message,
                FlightKeyMessage.class,
                FlightKeyMessage::version,
                removed -> {
                    FlightKey flightKey = flightKey(removed);
                    flightRemovalService.remove(flightKey);
                    // a published message expires like a flight of the day it was published, so
                    // cargo needs no retention setting of its own; a pending one always stays
                    outboxRepository.deletePublishedBefore(
                            flightKey
                                    .scheduledDepartureDate()
                                    .plusDays(1)
                                    .atStartOfDay(ZoneOffset.UTC)
                                    .toInstant());
                });
    }

    // parse, check the version, apply, then reject or retry
    private <T> void process(
            Message message, Class<T> type, Function<T, Integer> version, Consumer<T> apply) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        // flight's outbox always sets the amqp message id
        String messageId = message.getMessageProperties().getMessageId();
        log.debug("Received {} {}: {}", type.getSimpleName(), messageId, payload);

        T event = parse(payload, type);
        if (event == null) {
            rejectedMessageStore.store(messageId, payload, "Unreadable JSON or empty message");
            return;
        }
        // a missing version counts as unknown so we never guess the field layout
        Integer received = version.apply(event);
        if (!Objects.equals(received, SUPPORTED_VERSION)) {
            rejectedMessageStore.store(
                    messageId,
                    payload,
                    "Message version must be " + SUPPORTED_VERSION + ", was " + received);
            return;
        }

        try {
            apply.accept(event);
        } catch (FlightLookupUnavailableException
                | DataAccessException
                | TransactionException outage) {
            // outage so throw and let rabbit retry
            throw outage;
        } catch (InvalidCargoValueException
                | FlightLookupRejectedException
                | BookingRejectedException invalid) {
            // the reason column can't be null and not every exception has a message
            String reason =
                    Objects.requireNonNullElse(
                            invalid.getMessage(), invalid.getClass().getSimpleName());
            rejectedMessageStore.store(messageId, payload, reason);
        } catch (RuntimeException unexpected) {
            // a bug so reject instead of retrying forever
            log.error("Unexpected error processing flight event {}", messageId, unexpected);
            rejectedMessageStore.store(
                    messageId, payload, "Unexpected processing error: " + unexpected);
        }
    }

    // null for unreadable json or a json null body
    private <T> T parse(String payload, Class<T> type) {
        try {
            return jsonMapper.readValue(payload, type);
        } catch (JacksonException unreadable) {
            log.debug("Unreadable flight event: {}", unreadable.getOriginalMessage());
            return null;
        }
    }

    // flight creates every flight as planned with the schedule as its first estimate
    private static FlightInformation toFlight(FlightCreatedMessage created) {
        return new FlightInformation(
                flightKey(
                        created.flightNumber(),
                        created.scheduledDepartureDate(),
                        created.departureAirportCode()),
                new AirportCode(created.arrivalAirportCode()),
                FlightStatus.PLANNED,
                required(created.scheduledDeparture(), "Scheduled departure"),
                required(created.scheduledArrival(), "Scheduled arrival"),
                created.scheduledDeparture(),
                created.scheduledArrival());
    }

    private static FlightKey flightKey(FlightKeyMessage message) {
        return flightKey(
                message.flightNumber(),
                message.scheduledDepartureDate(),
                message.departureAirportCode());
    }

    // the value objects reject missing or invalid parts themselves
    private static FlightKey flightKey(
            String flightNumber, LocalDate scheduledDepartureDate, String departureAirportCode) {
        return new FlightKey(
                flightNumber, scheduledDepartureDate, new AirportCode(departureAirportCode));
    }

    // a missing json field is a rejection not a NullPointerException
    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new InvalidCargoValueException(field + " is required");
        }
        return value;
    }
}
