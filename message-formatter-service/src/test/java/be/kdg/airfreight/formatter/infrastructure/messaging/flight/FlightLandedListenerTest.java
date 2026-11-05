package be.kdg.airfreight.formatter.infrastructure.messaging.flight;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.FlightLandedFact;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlightLandedListenerTest {
    private static final UUID ID = UUID.fromString("6f1c2d3e-0000-4000-8000-000000000003");
    private static final Instant ATA = Instant.parse("2026-10-09T09:21:00Z");
    private static final Instant AT = Instant.parse("2026-10-09T09:22:00Z");

    private final MessageFormattingService service = mock(MessageFormattingService.class);
    private final FlightLandedListener listener = new FlightLandedListener(service);

    // the aa takes the arrival airport, not the departure airport of the key
    @Test
    void validMessageBecomesAFactForTheAa() {
        listener.receive(message(ID, 1, AT));

        verify(service)
                .prepareArrival(
                        ID,
                        new FlightLandedFact(
                                "HJ221", LocalDate.of(2026, 10, 9), "AMS", "SXBAA", ATA, AT));
    }

    @Test
    void unknownVersionIsRejectedAndPreparesNothing() {
        assertThatThrownBy(() -> listener.receive(message(ID, 2, AT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported FlightLanded version: 2");
        verifyNoInteractions(service);
    }

    @Test
    void missingTimestampIsRejected() {
        assertThatThrownBy(() -> listener.receive(message(ID, 1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FlightLanded must contain a message ID and timestamp");
        verifyNoInteractions(service);
    }

    private static FlightLandedMessage message(UUID id, int version, Instant occurredOn) {
        return new FlightLandedMessage(
                id,
                version,
                occurredOn,
                "HJ221",
                LocalDate.of(2026, 10, 9),
                "BRU",
                "AMS",
                "SXBAA",
                ATA);
    }
}
