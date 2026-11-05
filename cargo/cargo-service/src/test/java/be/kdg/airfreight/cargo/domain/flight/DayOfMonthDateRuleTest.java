package be.kdg.airfreight.cargo.domain.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DayOfMonthDateRuleTest {
    @ParameterizedTest(name = "day {0} received {1} -> {2}")
    @CsvSource({
        "9, 2027-01-09T10:00:00Z, 2027-01-09",
        "9, 2027-01-10T00:02:00Z, 2027-01-09",
        "9, 2027-01-08T23:58:00Z, 2027-01-09",
        "9, 2027-01-09T23:58:00Z, 2027-01-09",
        // no 31 feb so only jan and mar count
        "31, 2027-02-15T12:00:00Z, 2027-01-31",
        // 1 feb and 1 mar are both 14 days away so the future wins
        "1, 2027-02-15T12:00:00Z, 2027-03-01",
        "31, 2027-01-01T00:00:00Z, 2026-12-31",
        "1, 2026-12-31T23:00:00Z, 2027-01-01",
        "29, 2028-02-20T00:00:00Z, 2028-02-29",
        // already 15 feb in brussels but still 14 feb in utc
        "1, 2027-02-14T23:30:00Z, 2027-02-01"
    })
    void resolvesNearestDateInUtcPreferringTheFuture(
            int dayOfMonth, Instant receivedAt, LocalDate expected) {
        assertThat(DayOfMonthDateRule.resolve(dayOfMonth, receivedAt)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 32})
    void rejectsImpossibleDay(int dayOfMonth) {
        assertThatExceptionOfType(InvalidCargoValueException.class)
                .isThrownBy(
                        () ->
                                DayOfMonthDateRule.resolve(
                                        dayOfMonth, Instant.parse("2027-01-15T12:00:00Z")));
    }
}
