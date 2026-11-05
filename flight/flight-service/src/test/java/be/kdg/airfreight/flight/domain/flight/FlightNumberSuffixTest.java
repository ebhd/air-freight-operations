package be.kdg.airfreight.flight.domain.flight;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FlightNumberSuffixTest {
    @ParameterizedTest
    @CsvSource({"HJ500, HJ500A", "HJ500A, HJ500B", "HJ500Y, HJ500Z", "3V11, 3V11A"})
    void nextLetterFollowsTheNumber(String number, String expected) {
        assertThat(FlightNumberSuffix.next(number)).contains(expected);
    }

    @Test
    void afterZThereIsNoNextNumber() {
        assertThat(FlightNumberSuffix.next("HJ500Z")).isEmpty();
    }
}
