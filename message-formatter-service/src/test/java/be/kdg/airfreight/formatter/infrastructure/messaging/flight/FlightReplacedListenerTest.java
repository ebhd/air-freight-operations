package be.kdg.airfreight.formatter.infrastructure.messaging.flight;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.FlightReplacedFact;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// a thrown exception goes to partner.dlq through FormatterMessageRecoverer
class FlightReplacedListenerTest {
    private static final UUID ID = UUID.fromString("0b7d9e44-0000-4000-8000-000000000001");

    private final MessageFormattingService service = mock(MessageFormattingService.class);
    private final FlightReplacedListener listener = new FlightReplacedListener(service);

    @Test
    void validMessageBecomesAFact() {
        listener.receive(message(ID, 1));

        verify(service)
                .prepareFlightReplanned(
                        ID,
                        new FlightReplacedFact(
                                "HJ500",
                                LocalDate.of(2026, 11, 10),
                                "BRU",
                                "HJ500A",
                                LocalDate.of(2026, 11, 11),
                                "BRU",
                                Instant.parse("2026-11-11T05:00:00Z"),
                                Instant.parse("2026-11-11T10:20:00Z"),
                                Instant.parse("2026-11-10T14:03:15Z")));
    }

    @Test
    void unknownVersionIsRejectedAndPreparesNothing() {
        assertThatThrownBy(() -> listener.receive(message(ID, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported FlightReplaced version: 2");
        verifyNoInteractions(service);
    }

    @Test
    void missingMessageIdIsRejected() {
        assertThatThrownBy(() -> listener.receive(message(null, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FlightReplaced must contain a message ID and timestamp");
        verifyNoInteractions(service);
    }

    private static FlightReplacedMessage message(UUID id, int version) {
        return new FlightReplacedMessage(
                id,
                version,
                Instant.parse("2026-11-10T14:03:15Z"),
                "HJ500",
                LocalDate.of(2026, 11, 10),
                "BRU",
                "HJ500A",
                LocalDate.of(2026, 11, 11),
                "BRU",
                Instant.parse("2026-11-11T05:00:00Z"),
                Instant.parse("2026-11-11T10:20:00Z"));
    }
}
