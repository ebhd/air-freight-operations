package be.kdg.airfreight.partner.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MvtTimeRuleTest {
    private static final String ROW_NAME = "{0} read {1} -> {2}";
    private static final Instant READING = Instant.parse("2027-01-09T10:00:00Z");

    @ParameterizedTest(name = ROW_NAME)
    @CsvSource({
        "2355, 2027-01-10T00:02:00Z, 2027-01-09T23:55:00Z",
        "0015, 2027-01-08T23:58:00Z, 2027-01-09T00:15:00Z",
        // the time lands on 10 jan while the flight day stays 9 jan
        "0015, 2027-01-09T23:58:00Z, 2027-01-10T00:15:00Z",
        "1200, 2027-01-09T10:00:00Z, 2027-01-09T12:00:00Z"
    })
    void resolvesThePublishedExamples(String hhmm, Instant reference, Instant expected) {
        assertThat(MvtTimeRule.resolve(hhmm, reference)).isEqualTo(expected);
    }

    @ParameterizedTest(name = ROW_NAME)
    @CsvSource({
        "1200, 2027-01-09T00:00:00Z, 2027-01-09T12:00:00Z",
        "1200, 2027-01-09T10:00:00Z, 2027-01-09T12:00:00Z",
        "1200, 2027-01-09T23:59:00Z, 2027-01-09T12:00:00Z"
    })
    void noonHoldsForEveryReadingMomentOfThatDay(String hhmm, Instant reference, Instant expected) {
        assertThat(MvtTimeRule.resolve(hhmm, reference)).isEqualTo(expected);
    }

    @ParameterizedTest(name = ROW_NAME)
    @CsvSource({
        "0000, 2027-01-09T12:00:00Z, 2027-01-10T00:00:00Z",
        "1200, 2027-01-10T00:00:00Z, 2027-01-10T12:00:00Z"
    })
    void exactlyTwelveHoursAwayGoesToTheFuture(String hhmm, Instant reference, Instant expected) {
        assertThat(MvtTimeRule.resolve(hhmm, reference)).isEqualTo(expected);
    }

    @ParameterizedTest(name = ROW_NAME)
    @CsvSource({
        "0001, 2027-01-09T12:00:00Z, 2027-01-09T00:01:00Z",
        "2359, 2027-01-09T12:00:00Z, 2027-01-09T23:59:00Z",
        // 12:01 ahead would be outside, so it goes 11:59 back
        "1201, 2027-01-09T00:00:00Z, 2027-01-08T12:01:00Z",
        // the seconds of the reference count
        "0000, 2027-01-09T12:00:30Z, 2027-01-10T00:00:00Z",
        "0000, 2027-01-09T11:59:30Z, 2027-01-09T00:00:00Z",
        "1000, 2027-01-09T10:00:00Z, 2027-01-09T10:00:00Z"
    })
    void staysInsideTheTwelveHourWindow(String hhmm, Instant reference, Instant expected) {
        assertThat(MvtTimeRule.resolve(hhmm, reference)).isEqualTo(expected);
    }

    @ParameterizedTest(name = ROW_NAME)
    @CsvSource({
        "0010, 2027-01-31T23:50:00Z, 2027-02-01T00:10:00Z",
        "2350, 2027-02-01T00:05:00Z, 2027-01-31T23:50:00Z",
        "0010, 2026-12-31T23:50:00Z, 2027-01-01T00:10:00Z",
        "2355, 2027-01-01T00:05:00Z, 2026-12-31T23:55:00Z",
        // 2028 is a leap year, 2027 is not
        "0010, 2028-02-28T23:50:00Z, 2028-02-29T00:10:00Z",
        "0010, 2028-02-29T23:50:00Z, 2028-03-01T00:10:00Z",
        "0010, 2027-02-28T23:50:00Z, 2027-03-01T00:10:00Z"
    })
    void crossesMonthYearAndLeapDayEnds(String hhmm, Instant reference, Instant expected) {
        assertThat(MvtTimeRule.resolve(hhmm, reference)).isEqualTo(expected);
    }

    @ParameterizedTest(name = ROW_NAME)
    @CsvSource({
        // already 11 jul in brussels
        "2300, 2027-07-10T22:30:00Z, 2027-07-10T23:00:00Z",
        // this hour does not exist in brussels
        "0230, 2027-03-28T01:30:00Z, 2027-03-28T02:30:00Z"
    })
    void usesUtcAndNotBrusselsTime(String hhmm, Instant reference, Instant expected) {
        assertThat(MvtTimeRule.resolve(hhmm, reference)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2400", "2460", "1260", "123", "12345", "12a4", " 123", "-123"})
    void rejectsInvalidTimes(String hhmm) {
        assertThatIllegalArgumentException().isThrownBy(() -> MvtTimeRule.resolve(hhmm, READING));
    }

    @Test
    void rejectsMissingReference() {
        assertThatNullPointerException().isThrownBy(() -> MvtTimeRule.resolve("1200", null));
    }

    // this is why only the first reading may be used as reference
    @Test
    void aLaterReferenceWouldGiveAnotherDate() {
        var firstReading = Instant.parse("2027-01-09T23:58:00Z");
        var laterReading = Instant.parse("2027-01-11T10:00:00Z");

        assertThat(MvtTimeRule.resolve("0015", firstReading))
                .isEqualTo(Instant.parse("2027-01-10T00:15:00Z"));
        assertThat(MvtTimeRule.resolve("0015", laterReading))
                .isEqualTo(Instant.parse("2027-01-11T00:15:00Z"));
    }
}
