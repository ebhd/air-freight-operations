package be.kdg.airfreight.formatter.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ActualTimeFactTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 9);
    private static final Instant AT = Instant.parse("2026-10-09T07:18:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"HJ/221", "HJ.221", " "})
    void departedFactRefusesAFlightNumberThatBreaksLineTwo(String flightNumber) {
        assertThatThrownBy(() -> new FlightDepartedFact(flightNumber, DAY, "BRU", "SXBAA", AT, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("flight number must be nonblank single-line text without / or .");
    }

    @Test
    void landedFactRefusesARegistrationThatBreaksLineTwo() {
        assertThatThrownBy(() -> new FlightLandedFact("HJ221", DAY, "AMS", "SX.BAA", AT, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "aircraft registration must be nonblank single-line text without / or .");
    }

    @Test
    void airportMustBeThreeLetters() {
        assertThatThrownBy(() -> new FlightLandedFact("HJ221", DAY, "ams", "SXBAA", AT, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("airport code must be a three-letter IATA code");
    }

    @Test
    void actualTimeIsRequired() {
        assertThatThrownBy(() -> new FlightDepartedFact("HJ221", DAY, "BRU", "SXBAA", null, AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("ATD must not be null");
        assertThatThrownBy(() -> new FlightLandedFact("HJ221", DAY, "AMS", "SXBAA", null, AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("ATA must not be null");
    }

    @Test
    void plainValuesAreAccepted() {
        assertThatCode(() -> new FlightDepartedFact("HJ221", DAY, "BRU", "SX-BAA", AT, AT))
                .doesNotThrowAnyException();
        assertThatCode(() -> new FlightLandedFact("HJ221", DAY, "AMS", "SX-BAA", AT, AT))
                .doesNotThrowAnyException();
    }
}
