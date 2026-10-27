package be.kdg.airfreight.cargo.domain.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class FlightKeyTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 19);
    private static final AirportCode BRU = new AirportCode("BRU");

    @Test
    void trimsFlightNumberAndPrintsShortForm() {
        assertThat(new FlightKey(" HJ221 ", DATE, BRU)).hasToString("HJ221/2026-10-19/BRU");
    }

    @ParameterizedTest
    // slash would change the lookup url and 33 chars doesnt fit flights column
    @NullSource
    @ValueSource(strings = {"", "HJ/221", "HJ 221", "HJ2210000000000000000000000000000"})
    void rejectsFlightNumbersThatBreakTheLookup(String flightNumber) {
        assertThatExceptionOfType(InvalidCargoValueException.class)
                .isThrownBy(() -> new FlightKey(flightNumber, DATE, BRU));
    }

    @Test
    void rejectsMissingDateOrAirport() {
        assertThatExceptionOfType(InvalidCargoValueException.class)
                .isThrownBy(() -> new FlightKey("HJ221", null, BRU))
                .withMessage("Scheduled departure date is required");
        assertThatExceptionOfType(InvalidCargoValueException.class)
                .isThrownBy(() -> new FlightKey("HJ221", DATE, null))
                .withMessage("Departure airport code is required");
    }
}
