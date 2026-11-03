package be.kdg.airfreight.cargo.domain.flight;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FlightInformationTest {
    private static final FlightInformation FLIGHT =
            new FlightInformation(
                    new FlightKey("HJ221", LocalDate.of(2026, 10, 19), new AirportCode("BRU")),
                    new AirportCode("AMS"),
                    FlightStatus.PLANNED,
                    Instant.parse("2026-10-19T08:00:00Z"),
                    Instant.parse("2026-10-19T09:10:00Z"),
                    Instant.parse("2026-10-19T08:00:00Z"),
                    Instant.parse("2026-10-19T09:10:00Z"));

    @Test
    void newEstimatesChangeOnlyTheEstimates() {
        Instant etd = Instant.parse("2026-10-19T08:45:00Z");
        Instant eta = Instant.parse("2026-10-19T09:55:00Z");

        FlightInformation delayed = FLIGHT.withEstimates(etd, eta);

        assertThat(delayed.estimatedDeparture()).isEqualTo(etd);
        assertThat(delayed.estimatedArrival()).isEqualTo(eta);
        assertThat(delayed)
                .usingRecursiveComparison()
                .ignoringFields("estimatedDeparture", "estimatedArrival")
                .isEqualTo(FLIGHT);
    }
}
