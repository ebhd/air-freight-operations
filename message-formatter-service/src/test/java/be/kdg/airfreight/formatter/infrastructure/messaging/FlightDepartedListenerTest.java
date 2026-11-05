package be.kdg.airfreight.formatter.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.FlightDepartedFact;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlightDepartedListenerTest {
    private static final UUID ID = UUID.fromString("6f1c2d3e-0000-4000-8000-000000000002");
    private static final Instant ATD = Instant.parse("2026-10-09T07:18:00Z");
    private static final Instant AT = Instant.parse("2026-10-09T07:19:00Z");

    private final MessageFormattingService service = mock(MessageFormattingService.class);
    private final FlightDepartedListener listener = new FlightDepartedListener(service);

    @Test
    void validMessageBecomesAFactForTheAd() {
        listener.receive(message(ID, 1, AT));

        verify(service)
                .prepareDeparture(
                        ID,
                        new FlightDepartedFact(
                                "HJ221", LocalDate.of(2026, 10, 9), "BRU", "SXBAA", ATD, AT));
    }

    // the recoverer sends a failure that is not a publish outage to the dead letter queue
    @Test
    void unknownVersionIsRejectedAndPreparesNothing() {
        assertThatThrownBy(() -> listener.receive(message(ID, 2, AT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported FlightDeparted version: 2");
        verifyNoInteractions(service);
    }

    @Test
    void missingIdIsRejected() {
        assertThatThrownBy(() -> listener.receive(message(null, 1, AT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FlightDeparted must contain a message ID and timestamp");
        verifyNoInteractions(service);
    }

    private static FlightDepartedMessage message(UUID id, int version, Instant occurredOn) {
        return new FlightDepartedMessage(
                id,
                version,
                occurredOn,
                "HJ221",
                LocalDate.of(2026, 10, 9),
                "BRU",
                "AMS",
                "SXBAA",
                ATD);
    }
}
