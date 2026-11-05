package be.kdg.airfreight.simulator.replanning;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.kdg.airfreight.simulator.ssm.SsmPublisher;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

class FlightReplannedListenerTest {
    private final SsmPublisher publisher = mock(SsmPublisher.class);
    private final FlightReplannedListener listener = new FlightReplannedListener(publisher);

    @Test
    void validEventIsPublishedAsTheMappedSsm() {
        FlightReplannedMessage event =
                ReplanningSsmMapperTest.event("2026-11-11T05:00:00Z", "2026-11-11T10:20:00Z");

        listener.receive(event);

        verify(publisher).publish(ReplanningSsmMapper.toSsm(event));
    }

    @Test
    void otherVersionIsRejectedWithoutRetry() {
        FlightReplannedMessage event =
                new FlightReplannedMessage(
                        "6f1c2a0e",
                        2,
                        "HJ500",
                        LocalDate.of(2026, 11, 10),
                        "BRU",
                        "AMS",
                        "SXBAA",
                        "HJ500A",
                        Instant.parse("2026-11-11T05:00:00Z"),
                        Instant.parse("2026-11-11T10:20:00Z"));

        assertThatThrownBy(() -> listener.receive(event))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("version: 2");
        verifyNoInteractions(publisher);
    }

    @Test
    void missingNewScheduleIsRejectedWithoutRetry() {
        FlightReplannedMessage event =
                new FlightReplannedMessage(
                        "6f1c2a0e",
                        1,
                        "HJ500",
                        LocalDate.of(2026, 11, 10),
                        "BRU",
                        "AMS",
                        "SXBAA",
                        "HJ500A",
                        null,
                        Instant.parse("2026-11-11T10:20:00Z"));

        assertThatThrownBy(() -> listener.receive(event))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(publisher);
    }
}
