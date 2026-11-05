package be.kdg.airfreight.flight.infrastructure.messaging.cpm;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.kdg.airfreight.flight.application.finalizedload.FinalizedLoadService;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import be.kdg.airfreight.flight.domain.flight.InvalidScheduleException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// a thrown exception sends the message to flight.dlq through FlightMessageRecoverer
class CpmReceiverTest {
    private static final UUID ID = UUID.fromString("6f1c2d3e-0000-4000-8000-000000000001");
    private static final Instant AT = Instant.parse("2026-10-19T06:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);

    private final FinalizedLoadService service = mock(FinalizedLoadService.class);
    private final CpmReceiver receiver = new CpmReceiver(service);

    @Test
    void validMessageRecordsTheLoadOfThatFlight() {
        receiver.receive(new LoadFinalizedMessage(ID, 1, AT, "HJ221", DAY, "bru"));

        verify(service).recordFinalizedLoad(ID, new FlightKey("HJ221", DAY, "BRU"));
    }

    @Test
    void unknownVersionIsRejected() {
        assertThatThrownBy(
                        () ->
                                receiver.receive(
                                        new LoadFinalizedMessage(ID, 2, AT, "HJ221", DAY, "BRU")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported LoadFinalized version: 2");
        verifyNoInteractions(service);
    }

    // a missing version is 0 in the record
    @Test
    void missingVersionIsRejected() {
        assertThatThrownBy(
                        () ->
                                receiver.receive(
                                        new LoadFinalizedMessage(ID, 0, AT, "HJ221", DAY, "BRU")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }

    @Test
    void missingMessageIdIsRejected() {
        assertThatThrownBy(
                        () ->
                                receiver.receive(
                                        new LoadFinalizedMessage(null, 1, AT, "HJ221", DAY, "BRU")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LoadFinalized must contain a message ID and timestamp");
        verifyNoInteractions(service);
    }

    @Test
    void invalidAirportIsRejected() {
        assertThatThrownBy(
                        () ->
                                receiver.receive(
                                        new LoadFinalizedMessage(ID, 1, AT, "HJ221", DAY, "BR")))
                .isInstanceOf(InvalidScheduleException.class)
                .hasMessage("departure airport code must be a three-letter IATA code");
        verifyNoInteractions(service);
    }

    @Test
    void missingDateIsRejected() {
        assertThatThrownBy(
                        () ->
                                receiver.receive(
                                        new LoadFinalizedMessage(ID, 1, AT, "HJ221", null, "BRU")))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(service);
    }
}
