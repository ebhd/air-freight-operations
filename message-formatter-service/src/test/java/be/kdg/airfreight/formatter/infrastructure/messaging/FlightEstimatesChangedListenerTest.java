package be.kdg.airfreight.formatter.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlightEstimatesChangedListenerTest {
    private final MessageFormattingService service = mock(MessageFormattingService.class);
    private final FlightEstimatesChangedListener listener =
            new FlightEstimatesChangedListener(service);

    // the recoverer sends a failure that is not a database outage to the dead letter queue
    @Test
    void unknownVersionIsRejectedAndPreparesNothing() {
        var message =
                new FlightEstimatesChangedMessage(
                        UUID.randomUUID(),
                        2,
                        Instant.parse("2026-10-09T05:00:00Z"),
                        "HJ221",
                        LocalDate.of(2026, 10, 9),
                        "BRU",
                        "AMS",
                        "SXBAA",
                        Instant.parse("2026-10-09T06:45:00Z"),
                        Instant.parse("2026-10-09T08:45:00Z"),
                        Instant.parse("2026-10-09T07:15:00Z"),
                        Instant.parse("2026-10-09T09:15:00Z"));

        assertThatThrownBy(() -> listener.receive(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported FlightEstimatesChanged version: 2");
        verifyNoInteractions(service);
    }
}
