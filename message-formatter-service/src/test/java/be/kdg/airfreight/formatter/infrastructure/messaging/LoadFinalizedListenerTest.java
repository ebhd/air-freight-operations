package be.kdg.airfreight.formatter.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.kdg.airfreight.formatter.application.MessageFormattingService;
import be.kdg.airfreight.formatter.domain.LoadFinalizedFact;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// a thrown exception goes to partner.dlq through FormatterMessageRecoverer
class LoadFinalizedListenerTest {
    private static final UUID ID = UUID.fromString("6f1c2d3e-0000-4000-8000-000000000001");
    private static final Instant AT = Instant.parse("2026-11-20T06:55:00Z");

    private final MessageFormattingService service = mock(MessageFormattingService.class);
    private final LoadFinalizedListener listener = new LoadFinalizedListener(service);

    @Test
    void validMessageBecomesAFactForTheCpm() {
        listener.receive(message(ID, 1, List.of(uld())));

        ArgumentCaptor<LoadFinalizedFact> fact = ArgumentCaptor.forClass(LoadFinalizedFact.class);
        verify(service).prepareCpm(eq(ID), fact.capture());
        assertThat(fact.getValue().flightNumber()).isEqualTo("HJ221");
        assertThat(fact.getValue().ulds())
                .containsExactly(
                        new LoadFinalizedFact.FinalizedUld(
                                "AKE12345HJ", "CL", new BigDecimal("1250.0"), "KG", "AMS"));
    }

    @Test
    void unknownVersionIsRejectedAndPreparesNothing() {
        assertThatThrownBy(() -> listener.receive(message(ID, 2, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported LoadFinalized version: 2");
        verifyNoInteractions(service);
    }

    @Test
    void missingMessageIdIsRejected() {
        assertThatThrownBy(() -> listener.receive(message(null, 1, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LoadFinalized must contain a message ID and timestamp");
        verifyNoInteractions(service);
    }

    @Test
    void missingUldListIsRejected() {
        assertThatThrownBy(() -> listener.receive(message(ID, 1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LoadFinalized must contain a ULD list");
        verifyNoInteractions(service);
    }

    @Test
    void uldWithoutWeightIsRejected() {
        var noWeight = new LoadFinalizedMessage.Uld("AKE12345HJ", "CL", null, "AMS");

        assertThatThrownBy(() -> listener.receive(message(ID, 1, List.of(noWeight))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("every ULD must contain a weight");
        verifyNoInteractions(service);
    }

    private static LoadFinalizedMessage message(
            UUID id, int version, List<LoadFinalizedMessage.Uld> ulds) {
        return new LoadFinalizedMessage(
                id, version, AT, "HJ221", LocalDate.of(2026, 11, 20), "BRU", ulds);
    }

    private static LoadFinalizedMessage.Uld uld() {
        return new LoadFinalizedMessage.Uld(
                "AKE12345HJ",
                "CL",
                new LoadFinalizedMessage.Weight(new BigDecimal("1250.0"), "KG"),
                "AMS");
    }
}
