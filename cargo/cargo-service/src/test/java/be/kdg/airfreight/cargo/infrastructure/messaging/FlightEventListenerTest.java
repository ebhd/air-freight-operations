package be.kdg.airfreight.cargo.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.kdg.airfreight.cargo.application.cargoplan.MoveCargoPlanService;
import be.kdg.airfreight.cargo.application.flight.FlightCopyService;
import be.kdg.airfreight.cargo.domain.cargoplan.BookingRejectedException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.flight.FlightStatus;
import be.kdg.airfreight.cargo.infrastructure.persistence.RejectedMessageStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tools.jackson.databind.json.JsonMapper;

class FlightEventListenerTest {
    private static final String MESSAGE_ID = "0b9c7f8e-3c1a-4e57-9a51-2f6d5d3b8a10";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final FlightKey KEY =
            new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU"));
    private static final Instant OCCURRED_ON = Instant.parse("2026-10-19T07:00:00Z");
    private static final Instant STD = Instant.parse("2026-10-19T08:00:00Z");
    private static final Instant STA = Instant.parse("2026-10-19T09:10:00Z");
    private static final Instant ETD = Instant.parse("2026-10-19T08:45:00Z");
    private static final Instant ETA = Instant.parse("2026-10-19T09:55:00Z");

    private final FlightCopyService copyService = mock(FlightCopyService.class);
    private final MoveCargoPlanService moveService = mock(MoveCargoPlanService.class);
    private final RejectedMessageStore rejectedMessages = mock(RejectedMessageStore.class);
    private final FlightEventListener listener =
            new FlightEventListener(copyService, moveService, rejectedMessages, JSON);

    @Test
    void createdFlightIsPlannedWithTheScheduleAsEstimates() {
        listener.receiveCreated(message(created()));

        verify(copyService)
                .recordCreated(
                        new FlightInformation(
                                KEY,
                                new AirportCode("AMS"),
                                FlightStatus.PLANNED,
                                STD,
                                STA,
                                STD,
                                STA),
                        OCCURRED_ON);
        verifyNoInteractions(rejectedMessages);
    }

    @Test
    void newEstimatesAreRecorded() {
        listener.receiveEstimates(message(estimates()));

        verify(copyService).recordEstimates(KEY, ETD, ETA, OCCURRED_ON);
        verifyNoInteractions(rejectedMessages);
    }

    // flights full departed json, cargo reads only the key
    @Test
    void departedFlightIsRecordedByItsKey() {
        listener.receiveDeparted(message(departed("version", 1)));

        verify(copyService).recordDeparted(KEY);
        verifyNoInteractions(rejectedMessages);
    }

    @Test
    void landedFlightIsRecordedByItsKey() {
        listener.receiveLanded(message(landed("version", 1)));

        verify(copyService).recordLanded(KEY);
        verifyNoInteractions(rejectedMessages);
    }

    @Test
    void departedWithAnUnknownVersionIsRejected() {
        String payload = departed("version", 2);

        listener.receiveDeparted(message(payload));

        verify(rejectedMessages).store(MESSAGE_ID, payload, "Message version must be 1, was 2");
        verifyNoInteractions(copyService);
    }

    @Test
    void landedWithoutAnAirportIsRejectedWithTheReason() {
        String payload = landed("departureAirportCode", null);

        listener.receiveLanded(message(payload));

        verify(rejectedMessages).store(eq(MESSAGE_ID), eq(payload), anyString());
        verifyNoInteractions(copyService);
    }

    @Test
    void databaseOutageDuringDepartedIsRetriedNotRejected() {
        doThrow(new DataAccessResourceFailureException("database down"))
                .when(copyService)
                .recordDeparted(any());

        assertThatThrownBy(() -> listener.receiveDeparted(message(departed("version", 1))))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void replannedFlightGetsItsStatusInTheCopy() {
        listener.receiveReplanned(message(replanned("version", 1)));

        verify(copyService).recordReplanned(KEY, OCCURRED_ON);
        verifyNoInteractions(rejectedMessages, moveService);
    }

    @Test
    void replacedFlightMovesItsCargo() {
        listener.receiveReplaced(message(replaced("version", 1)));

        verify(moveService)
                .move(
                        KEY,
                        new FlightKey(
                                "HJ221A", LocalDate.of(2026, 10, 20), new AirportCode("BRU")));
        verifyNoInteractions(rejectedMessages, copyService);
    }

    @Test
    void replacedWithAnotherVersionIsRejected() {
        String payload = replaced("version", 2);

        listener.receiveReplaced(message(payload));

        verify(rejectedMessages).store(MESSAGE_ID, payload, "Message version must be 1, was 2");
        verifyNoInteractions(moveService);
    }

    // the move hit a finalized new plan, retrying would not change that
    @Test
    void refusedMoveIsRejectedNotRetried() {
        doThrow(new BookingRejectedException("that cargo plan is FINALIZED"))
                .when(moveService)
                .move(any(), any());
        String payload = replaced("version", 1);

        assertThatCode(() -> listener.receiveReplaced(message(payload))).doesNotThrowAnyException();

        verify(rejectedMessages).store(MESSAGE_ID, payload, "that cargo plan is FINALIZED");
    }

    @Test
    void moveDuringAConcurrentChangeIsRetried() {
        doThrow(new ObjectOptimisticLockingFailureException("cargo_plans", "HJ221"))
                .when(moveService)
                .move(any(), any());

        assertThatThrownBy(() -> listener.receiveReplaced(message(replaced("version", 1))))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(rejectedMessages, never()).store(any(), anyString(), anyString());
    }

    // returning normally acknowledges the message so rabbit never retries it
    @Test
    void unreadableMessageIsRejectedNotRetried() {
        assertThatCode(() -> listener.receiveCreated(message("{not json")))
                .doesNotThrowAnyException();

        verify(rejectedMessages).store(MESSAGE_ID, "{not json", "Unreadable JSON or empty message");
        verifyNoInteractions(copyService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void unknownVersionIsRejected(int version) {
        String payload = estimates("version", version);

        listener.receiveEstimates(message(payload));

        verify(rejectedMessages)
                .store(MESSAGE_ID, payload, "Message version must be 1, was " + version);
        verifyNoInteractions(copyService);
    }

    @Test
    void missingVersionIsRejected() {
        String payload = created("version", null);

        listener.receiveCreated(message(payload));

        verify(rejectedMessages).store(MESSAGE_ID, payload, "Message version must be 1, was null");
        verifyNoInteractions(copyService);
    }

    @Test
    void missingEstimateIsRejectedWithItsName() {
        String payload = estimates("estimatedDeparture", null);

        listener.receiveEstimates(message(payload));

        verify(rejectedMessages).store(MESSAGE_ID, payload, "Estimated departure is required");
        verifyNoInteractions(copyService);
    }

    @Test
    void invalidAirportIsRejectedWithTheReason() {
        String payload = created("arrivalAirportCode", "BR");

        listener.receiveCreated(message(payload));

        verify(rejectedMessages)
                .store(MESSAGE_ID, payload, "Airport code must be three letters: BR");
        verifyNoInteractions(copyService);
    }

    @Test
    void flightOutageIsRetriedNotRejected() {
        doThrow(new FlightLookupUnavailableException(KEY, null))
                .when(copyService)
                .recordEstimates(any(), any(), any(), any());

        assertThatThrownBy(() -> listener.receiveEstimates(message(estimates())))
                .isInstanceOf(FlightLookupUnavailableException.class);

        verify(rejectedMessages, never()).store(any(), anyString(), anyString());
    }

    @Test
    void databaseOutageIsRetriedNotRejected() {
        doThrow(new DataAccessResourceFailureException("database down"))
                .when(copyService)
                .recordCreated(any(), any());

        assertThatThrownBy(() -> listener.receiveCreated(message(created())))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(rejectedMessages, never()).store(any(), anyString(), anyString());
    }

    // a bug would otherwise be retried forever
    @Test
    void unexpectedErrorIsRejected() {
        doThrow(new IllegalStateException("bug")).when(copyService).recordCreated(any(), any());
        String payload = created();

        listener.receiveCreated(message(payload));

        verify(rejectedMessages)
                .store(eq(MESSAGE_ID), eq(payload), startsWith("Unexpected processing error: "));
    }

    private static String created() {
        return created("version", 1);
    }

    // flights full json, also the fields cargo does not read
    // one field replaced, null writes a json null like a missing field
    private static String created(String field, Object value) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", MESSAGE_ID);
        message.put("version", 1);
        message.put("occurredOn", OCCURRED_ON.toString());
        message.put("flightNumber", "HJ221");
        message.put("scheduledDepartureDate", "2026-10-19");
        message.put("departureAirportCode", "BRU");
        message.put("arrivalAirportCode", "AMS");
        message.put("scheduledDeparture", STD.toString());
        message.put("scheduledArrival", STA.toString());
        message.put(field, value);
        return JSON.writeValueAsString(message);
    }

    private static String estimates() {
        return estimates("version", 1);
    }

    // flights full json like created
    private static String estimates(String field, Object value) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", MESSAGE_ID);
        message.put("version", 1);
        message.put("occurredOn", OCCURRED_ON.toString());
        message.put("flightNumber", "HJ221");
        message.put("scheduledDepartureDate", "2026-10-19");
        message.put("departureAirportCode", "BRU");
        message.put("arrivalAirportCode", "AMS");
        message.put("aircraftRegistration", "OO-ABC");
        message.put("previousEstimatedDeparture", STD.toString());
        message.put("previousEstimatedArrival", STA.toString());
        message.put("estimatedDeparture", ETD.toString());
        message.put("estimatedArrival", ETA.toString());
        message.put(field, value);
        return JSON.writeValueAsString(message);
    }

    // flights full departed json, one field replaced
    private static String departed(String field, Object value) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", MESSAGE_ID);
        message.put("version", 1);
        message.put("occurredOn", OCCURRED_ON.toString());
        message.put("flightNumber", "HJ221");
        message.put("scheduledDepartureDate", "2026-10-19");
        message.put("departureAirportCode", "BRU");
        message.put("arrivalAirportCode", "AMS");
        message.put("aircraftRegistration", "OO-ABC");
        message.put("actualDeparture", "2026-10-19T08:03:00Z");
        message.put(field, value);
        return JSON.writeValueAsString(message);
    }

    private static String landed(String field, Object value) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", MESSAGE_ID);
        message.put("version", 1);
        message.put("occurredOn", OCCURRED_ON.toString());
        message.put("flightNumber", "HJ221");
        message.put("scheduledDepartureDate", "2026-10-19");
        message.put("departureAirportCode", "BRU");
        message.put("arrivalAirportCode", "AMS");
        message.put("aircraftRegistration", "OO-ABC");
        message.put("actualArrival", "2026-10-19T09:58:00Z");
        message.put(field, value);
        return JSON.writeValueAsString(message);
    }

    // flights full json for flight.replanned
    private static String replanned(String field, Object value) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", MESSAGE_ID);
        message.put("version", 1);
        message.put("occurredOn", OCCURRED_ON.toString());
        message.put("flightNumber", "HJ221");
        message.put("scheduledDepartureDate", "2026-10-19");
        message.put("departureAirportCode", "BRU");
        message.put("arrivalAirportCode", "AMS");
        message.put("aircraftRegistration", "OO-ABC");
        message.put("scheduledDeparture", STD.toString());
        message.put("scheduledArrival", STA.toString());
        message.put("newFlightNumber", "HJ221A");
        message.put("newScheduledDepartureDate", "2026-10-20");
        message.put("newScheduledDeparture", "2026-10-20T05:00:00Z");
        message.put("newScheduledArrival", "2026-10-20T10:20:00Z");
        message.put(field, value);
        return JSON.writeValueAsString(message);
    }

    // flights full json for flight.replaced
    private static String replaced(String field, Object value) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", MESSAGE_ID);
        message.put("version", 1);
        message.put("occurredOn", OCCURRED_ON.toString());
        message.put("flightNumber", "HJ221");
        message.put("scheduledDepartureDate", "2026-10-19");
        message.put("departureAirportCode", "BRU");
        message.put("newFlightNumber", "HJ221A");
        message.put("newScheduledDepartureDate", "2026-10-20");
        message.put("newDepartureAirportCode", "BRU");
        message.put("newScheduledDeparture", "2026-10-20T05:00:00Z");
        message.put("newScheduledArrival", "2026-10-20T10:20:00Z");
        message.put(field, value);
        return JSON.writeValueAsString(message);
    }

    private static Message message(String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(MESSAGE_ID);
        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }
}
