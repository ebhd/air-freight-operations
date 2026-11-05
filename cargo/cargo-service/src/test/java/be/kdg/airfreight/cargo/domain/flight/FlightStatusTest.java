package be.kdg.airfreight.cargo.domain.flight;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FlightStatusTest {
    // planned, departed, landed in that order and never back
    @Test
    void eachStatusKnowsTheStatusesItMayFollow() {
        assertThat(FlightStatus.PLANNED.predecessors()).isEmpty();
        assertThat(FlightStatus.DEPARTED.predecessors()).containsExactly(FlightStatus.PLANNED);
        assertThat(FlightStatus.LANDED.predecessors())
                .containsExactlyInAnyOrder(FlightStatus.PLANNED, FlightStatus.DEPARTED);
        assertThat(FlightStatus.REPLANNED.predecessors()).containsExactly(FlightStatus.PLANNED);
    }
}
