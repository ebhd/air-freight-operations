package be.kdg.airfreight.flight.domain.flight;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class FlightScheduleTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 12);

    @Test
    void expandsInclusiveRangeForSelectedWeekdays() {
        var schedule =
                schedule(
                        START,
                        START.plusDays(2),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        DayOfWeek.MONDAY,
                        DayOfWeek.WEDNESDAY);

        var flights = schedule.expand();

        assertThat(flights).hasSize(2);
        assertThat(flights)
                .extracting(flight -> flight.key().scheduledDepartureDate())
                .containsExactly(START, START.plusDays(2));
    }

    @Test
    void arrivalBeforeDepartureUsesFollowingDay() {
        var flight =
                schedule(START, START, LocalTime.of(23, 30), LocalTime.of(1, 0), DayOfWeek.MONDAY)
                        .expand()
                        .getFirst();

        assertThat(flight.scheduledDeparture()).isEqualTo(Instant.parse("2026-10-12T23:30:00Z"));
        assertThat(flight.scheduledArrival()).isEqualTo(Instant.parse("2026-10-13T01:00:00Z"));
    }

    @Test
    void arrivalEqualToDepartureUsesFollowingDay() {
        var flight =
                schedule(START, START, LocalTime.NOON, LocalTime.NOON, DayOfWeek.MONDAY)
                        .expand()
                        .getFirst();

        assertThat(flight.scheduledArrival()).isEqualTo(Instant.parse("2026-10-13T12:00:00Z"));
    }

    @Test
    void createsPlannedFlightWithStableUtcIdentity() {
        var flight =
                schedule(START, START, LocalTime.of(8, 0), LocalTime.of(10, 0), DayOfWeek.MONDAY)
                        .expand()
                        .getFirst();

        assertThat(flight.status()).isEqualTo(FlightStatus.PLANNED);
        assertThat(flight.key()).isEqualTo(new FlightKey("HJ221", START, "BRU"));
        assertThat(flight.key().scheduledDepartureDate()).isEqualTo(START);
    }

    private FlightSchedule schedule(
            LocalDate start,
            LocalDate end,
            LocalTime departure,
            LocalTime arrival,
            DayOfWeek... days) {
        var operatingDays =
                days.length == 0 ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.of(days[0]);
        if (days.length > 1) {
            operatingDays.add(days[1]);
        }
        return new FlightSchedule(
                start, end, operatingDays, departure, arrival, "HJ221", "bru", "ams", "SXBAA");
    }
}
