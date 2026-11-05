package be.kdg.airfreight.cargo.infrastructure.messaging.uld;

import static be.kdg.airfreight.cargo.support.TestHelper.restoredPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.kdg.airfreight.cargo.application.booking.BookUldCommand;
import be.kdg.airfreight.cargo.application.booking.BookUldService;
import be.kdg.airfreight.cargo.application.booking.ProcessedMessageRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanState;
import be.kdg.airfreight.cargo.domain.exception.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.infrastructure.persistence.MessageReceipts;
import be.kdg.airfreight.cargo.infrastructure.persistence.RejectedMessageStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import tools.jackson.databind.json.JsonMapper;

class UldRabbitListenerTest {
    private static final String MESSAGE_ID = "uld-1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final FlightRepository flights = mock(FlightRepository.class);
    private final CargoPlanRepository cargoPlans = mock(CargoPlanRepository.class);
    private final RejectedMessageStore rejectedMessages = mock(RejectedMessageStore.class);
    private final MessageReceipts receipts = mock(MessageReceipts.class);
    private final UldRabbitListener listener =
            new UldRabbitListener(
                    new BookUldService(
                            flights,
                            cargoPlans,
                            mock(ProcessedMessageRepository.class),
                            Clock.systemUTC()),
                    rejectedMessages,
                    receipts,
                    JSON,
                    Clock.systemUTC());

    @BeforeEach
    void firstReceipt() {
        when(receipts.firstReceivedAt(eq(MESSAGE_ID), any()))
                .thenReturn(Instant.parse("2026-10-15T10:00:00Z"));
    }

    static Stream<Arguments> invalidValues() {
        String tooLongUldCode = "A".repeat(33);
        return Stream.of(
                arguments("departureAirportCode", "BR", "Airport code must be three letters: BR"),
                arguments("destination", "A1S", "Airport code must be three letters: A1S"),
                arguments(
                        "weight",
                        Map.of("value", -1, "unit", "KG"),
                        "Weight must be greater than zero"),
                arguments(
                        "weight",
                        Map.of("value", 1.2345, "unit", "KG"),
                        "Weight must have at most 9 digits before and 3 after the decimal point"),
                arguments(
                        "weight",
                        Map.of("value", 100, "unit", "TON"),
                        "Unsupported weight unit: TON"),
                arguments("weight", Map.of("value", 100), "Weight unit is required"),
                arguments("weight", null, "Weight is required"),
                arguments("uldCode", null, "ULD code is required"),
                arguments(
                        "uldCode",
                        tooLongUldCode,
                        "ULD code must be 1 to 32 characters: " + tooLongUldCode),
                arguments("flightNumber", null, "Flight number is required"),
                arguments(
                        "flightNumber",
                        "HJ-221",
                        "Flight number must be 1 to 32 letters or digits: HJ-221"),
                arguments("scheduledDepartureDay", 32, "Day of month must be between 1 and 31"));
    }

    // returning normally acknowledges the message so rabbit never retries it
    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("invalidValues")
    void invalidValueIsRejectedNotRetried(String field, Object value, String reason) {
        String payload = validMessageWith(field, value);

        assertThatCode(() -> listener.receive(message(payload))).doesNotThrowAnyException();

        verify(rejectedMessages).store(MESSAGE_ID, payload, reason);
        verifyNoInteractions(flights, cargoPlans);
    }

    @Test
    void flightOutageIsRetriedNotRejected() {
        FlightKey flightKey =
                new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));
        when(flights.findByFlightKey(flightKey))
                .thenThrow(new FlightLookupUnavailableException(flightKey, null));
        String payload = validMessageWith("uldCode", "AKE12345KL");

        assertThatThrownBy(() -> listener.receive(message(payload)))
                .isInstanceOf(FlightLookupUnavailableException.class);

        verify(rejectedMessages, never()).store(any(), any(), any());
    }

    static Stream<DataAccessException> concurrentPlanChanges() {
        return Stream.of(
                // a position change committed first so the plan version moved on
                new OptimisticLockingFailureException("cargo plan version changed"),
                // another first booking created the plan row first
                new DataIntegrityViolationException("duplicate key on cargo_plans"));
    }

    // the retry reads the plan again so it succeeds and the message is never rejected
    @ParameterizedTest
    @MethodSource("concurrentPlanChanges")
    void concurrentPlanChangeIsRetriedNotRejected(DataAccessException conflict) {
        FlightKey flightKey =
                new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));
        Instant std = Instant.parse("2026-10-19T08:00:00Z");
        when(flights.findByFlightKey(flightKey))
                .thenReturn(
                        Optional.of(
                                new FlightInformation(
                                        flightKey,
                                        new AirportCode("AMS"),
                                        FlightStatus.PLANNED,
                                        std,
                                        std.plusSeconds(7200),
                                        std,
                                        std.plusSeconds(7200))));
        when(cargoPlans.findByFlightKey(flightKey)).thenReturn(Optional.empty());
        doThrow(conflict).when(cargoPlans).save(any(CargoPlan.class));
        String payload = validMessageWith("uldCode", "AKE12345KL");

        assertThatThrownBy(() -> listener.receive(message(payload))).isSameAs(conflict);

        verify(rejectedMessages, never()).store(any(), any(), any());
    }

    // a finalized load takes no new uld so the message is kept with the reason
    @Test
    void bookingForAFinalizedPlanIsRejectedNotRetried() {
        FlightKey flightKey =
                new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));
        Instant std = Instant.parse("2026-10-19T08:00:00Z");
        when(flights.findByFlightKey(flightKey))
                .thenReturn(
                        Optional.of(
                                new FlightInformation(
                                        flightKey,
                                        new AirportCode("AMS"),
                                        FlightStatus.PLANNED,
                                        std,
                                        std.plusSeconds(7200),
                                        std,
                                        std.plusSeconds(7200))));
        when(cargoPlans.findByFlightKey(flightKey))
                .thenReturn(
                        Optional.of(
                                restoredPlan(
                                        flightKey,
                                        CargoPlanState.FINALIZED,
                                        Instant.parse("2026-10-19T06:00:00Z"),
                                        List.of())));
        String payload = validMessageWith("uldCode", "AKE12345KL");

        assertThatCode(() -> listener.receive(message(payload))).doesNotThrowAnyException();

        verify(rejectedMessages)
                .store(
                        MESSAGE_ID,
                        payload,
                        "Cargo plan of flight HJ221/2026-10-19/BRU is finalized, no ULD can be"
                                + " added");
        verify(cargoPlans, never()).save(any());
    }

    // the old flight of a replanning takes no bookings, the reason names the new flight to use
    @Test
    void bookingForAReplannedFlightIsRejectedNotRetried() {
        FlightKey flightKey =
                new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));
        Instant std = Instant.parse("2026-10-19T08:00:00Z");
        when(flights.findByFlightKey(flightKey))
                .thenReturn(
                        Optional.of(
                                new FlightInformation(
                                        flightKey,
                                        new AirportCode("AMS"),
                                        FlightStatus.REPLANNED,
                                        std,
                                        std.plusSeconds(7200),
                                        std,
                                        std.plusSeconds(7200))));
        String payload = validMessageWith("uldCode", "AKE12345KL");

        assertThatCode(() -> listener.receive(message(payload))).doesNotThrowAnyException();

        verify(rejectedMessages)
                .store(
                        MESSAGE_ID,
                        payload,
                        "Flight HJ221/2026-10-19/BRU was replanned, book on the new flight");
        verify(cargoPlans, never()).save(any());
    }

    private static String validMessageWith(String field, Object value) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", MESSAGE_ID);
        message.put("version", 1);
        message.put("flightNumber", "HJ221");
        message.put("scheduledDepartureDay", 19);
        message.put("departureAirportCode", "BRU");
        message.put("uldCode", "AKE12345KL");
        message.put("weight", Map.of("value", 1250.5, "unit", "KG"));
        message.put("destination", "AMS");
        message.put(field, value);
        return JSON.writeValueAsString(message);
    }

    private static Message message(String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(MESSAGE_ID);
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Nested
    class VersionHandling {

        private static final String MESSAGE_ID = "uld-0001";

        private final BookUldService bookUldService = mock(BookUldService.class);
        private final RejectedMessageStore rejectedMessageStore = mock(RejectedMessageStore.class);
        private final MessageReceipts messageReceipts = mock(MessageReceipts.class);
        private final UldRabbitListener listener =
                new UldRabbitListener(
                        bookUldService,
                        rejectedMessageStore,
                        messageReceipts,
                        JsonMapper.builder().build(),
                        Clock.systemUTC());

        @Test
        void booksAVersionOneMessage() {
            when(messageReceipts.firstReceivedAt(eq(MESSAGE_ID), any()))
                    .thenReturn(Instant.parse("2026-10-18T10:00:00Z"));

            listener.receive(message(payload("\"version\": 1,")));

            ArgumentCaptor<BookUldCommand> command = ArgumentCaptor.forClass(BookUldCommand.class);
            verify(bookUldService).bookUld(command.capture());
            assertThat(command.getValue().messageId()).isEqualTo(MESSAGE_ID);
            assertThat(command.getValue().scheduledDepartureDay()).isEqualTo(19);
            verify(rejectedMessageStore, never()).store(any(), any(), any());
        }

        @Test
        void rejectsAMessageWithoutVersion() {
            String payload = payload("");

            listener.receive(message(payload));

            verify(rejectedMessageStore)
                    .store(MESSAGE_ID, payload, "Message version must be 1, was null");
            verifyNoInteractions(bookUldService, messageReceipts);
        }

        @Test
        void rejectsAnUnknownVersion() {
            String payload = payload("\"version\": 2,");

            listener.receive(message(payload));

            verify(rejectedMessageStore)
                    .store(MESSAGE_ID, payload, "Message version must be 1, was 2");
            verifyNoInteractions(bookUldService, messageReceipts);
        }

        private static Message message(String payload) {
            MessageProperties properties = new MessageProperties();
            properties.setMessageId(MESSAGE_ID);
            return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
        }

        private static String payload(String versionField) {
            return """
                    {"messageId": "uld-0001", %s "flightNumber": "HJ221", "scheduledDepartureDay": 19,
                     "departureAirportCode": "BRU", "uldCode": "AKE12345KL",
                     "weight": {"value": 1250.5, "unit": "KG"}, "destination": "AMS"}
                    """
                    .formatted(versionField);
        }
    }
}
