package be.kdg.airfreight.cargo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DayOfMonthDateRuleTest {
    @ParameterizedTest(name = "day {0} received {1} -> {2}")
    @CsvSource({
        // the flight date is the nearest date to the reading moment.
        "9, 2027-01-09T10:00:00Z, 2027-01-09",
        "9, 2027-01-10T00:02:00Z, 2027-01-09",
        "9, 2027-01-08T23:58:00Z, 2027-01-09",
        // February has no 31st, so only January and March are candidates.
        "31, 2027-02-15T12:00:00Z, 2027-01-31",
        // 1 February and 1 March are both 14 days away: the future date wins.
        "1, 2027-02-15T12:00:00Z, 2027-03-01",
        // Year boundaries in both directions.
        "31, 2027-01-01T00:00:00Z, 2026-12-31",
        "1, 2026-12-31T23:00:00Z, 2027-01-01",
        // Leap year.
        "29, 2028-02-20T00:00:00Z, 2028-02-29",
        // 00:30 on 15 February in Brussels, but still 14 February in UTC: 1 February is nearer.
        // Using the Brussels date would make it a tie and resolve to 1 March.
        "1, 2027-02-14T23:30:00Z, 2027-02-01"
    })
    void resolvesNearestDateInUtcPreferringTheFuture(
            int dayOfMonth, Instant receivedAt, LocalDate expected) {
        assertThat(DayOfMonthDateRule.resolve(dayOfMonth, receivedAt)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 32})
    void rejectsImpossibleDay(int dayOfMonth) {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                DayOfMonthDateRule.resolve(
                                        dayOfMonth, Instant.parse("2027-01-15T12:00:00Z")));
    }
}
