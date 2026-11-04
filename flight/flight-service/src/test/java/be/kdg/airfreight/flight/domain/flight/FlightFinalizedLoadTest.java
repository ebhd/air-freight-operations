package be.kdg.airfreight.flight.domain.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlightFinalizedLoadTest {
    private static final FlightKey KEY = new FlightKey("HJ221", LocalDate.of(2026, 10, 19), "BRU");
    private static final Instant STD = Instant.parse("2026-10-19T08:00:00Z");
    private static final Instant STA = Instant.parse("2026-10-19T10:00:00Z");

    @Test
    void newFlightHasNoProcessedCpm() {
        assertThat(planned().finalizedLoadProcessed()).isFalse();
    }

    // the gate issue 28 calls before it records atd
    @Test
    void departureGateRefusesWithAReasonUntilTheCpmIsProcessed() {
        Flight flight = planned();

        assertThatThrownBy(flight::requireFinalizedLoadProcessed)
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessage(
                        "departure needs the processed CPM of the finalized load from Cargo"
                                + " Loading");

        flight.recordFinalizedLoad();

        assertThatCode(flight::requireFinalizedLoadProcessed).doesNotThrowAnyException();
    }

    @Test
    void secondRecordHasNoEffect() {
        Flight flight = planned();

        assertThat(flight.recordFinalizedLoad()).isTrue();
        assertThat(flight.recordFinalizedLoad()).isFalse();
        assertThat(flight.finalizedLoadProcessed()).isTrue();
    }

    @Test
    void cpmForAFlightThatIsNoLongerPlannedIsRefused() {
        Flight departed =
                Flight.restore(
                        KEY, "AMS", "SXBAA", STD, STA, FlightStatus.DEPARTED, List.of(), List.of());

        assertThatThrownBy(departed::recordFinalizedLoad)
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessage("a finalized load can only be recorded while the flight is planned");
        assertThat(departed.finalizedLoadProcessed()).isFalse();
    }

    // a redelivery after departure still has no effect instead of an error
    @Test
    void processedFlightIgnoresARepeatAfterDeparture() {
        Flight departed =
                Flight.restore(
                        KEY,
                        "AMS",
                        "SXBAA",
                        STD,
                        STA,
                        FlightStatus.DEPARTED,
                        List.of(),
                        List.of(),
                        true);

        assertThat(departed.recordFinalizedLoad()).isFalse();
    }

    private static Flight planned() {
        return Flight.create(KEY, "AMS", "SXBAA", STD, STA);
    }
}
