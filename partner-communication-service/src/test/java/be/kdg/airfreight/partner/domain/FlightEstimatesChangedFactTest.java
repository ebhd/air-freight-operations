package be.kdg.airfreight.partner.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlightEstimatesChangedFactTest {
    @ParameterizedTest
    @ValueSource(strings = {"HJ/221", "HJ.221"})
    void flightNumberWithAnMvtSeparatorIsRefused(String flightNumber) {
        assertThatThrownBy(() -> fact(flightNumber, "SXBAA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("flight number must be nonblank single-line text without / or .");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SX/BAA", "SX.BAA"})
    void registrationWithAnMvtSeparatorIsRefused(String registration) {
        assertThatThrownBy(() -> fact("HJ221", registration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "aircraft registration must be nonblank single-line text without / or .");
    }

    @Test
    void plainFlightNumberAndRegistrationAreAccepted() {
        fact("HJ221", "SX-BAA");
    }

    private static FlightEstimatesChangedFact fact(String flightNumber, String registration) {
        return new FlightEstimatesChangedFact(
                flightNumber,
                LocalDate.of(2026, 10, 9),
                "BRU",
                "AMS",
                registration,
                Instant.parse("2026-10-09T06:45:00Z"),
                Instant.parse("2026-10-09T08:45:00Z"),
                Instant.parse("2026-10-09T07:15:00Z"),
                Instant.parse("2026-10-09T09:15:00Z"),
                Instant.parse("2026-10-09T05:00:00Z"));
    }
}
