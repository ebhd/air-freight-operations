package be.kdg.airfreight.simulator.replanning;

import static org.assertj.core.api.Assertions.assertThat;

import be.kdg.airfreight.simulator.ssm.SsmMessage;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReplanningSsmMapperTest {
    // the plan example: HJ500 of 10 nov becomes HJ500A departing 11 nov 05:00Z
    @Test
    void newScheduleBecomesAOneDaySsmWithUtcTimes() {
        SsmMessage ssm =
                ReplanningSsmMapper.toSsm(event("2026-11-11T05:00:00Z", "2026-11-11T10:20:00Z"));

        assertThat(ssm.messageId()).isEqualTo("replan-6f1c2a0e");
        assertThat(ssm.startDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(ssm.endDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(ssm.operatingDays()).isEqualTo(Set.of(DayOfWeek.WEDNESDAY));
        assertThat(ssm.departureTime()).isEqualTo(LocalTime.of(5, 0));
        assertThat(ssm.arrivalTime()).isEqualTo(LocalTime.of(10, 20));
        assertThat(ssm.flightNumber()).isEqualTo("HJ500A");
        assertThat(ssm.departureAirportCode()).isEqualTo("BRU");
        assertThat(ssm.arrivalAirportCode()).isEqualTo("AMS");
        assertThat(ssm.aircraftRegistration()).isEqualTo("SXBAA");
    }

    // only the times go in the ssm, flight makes an arrival time not after departure the next day
    @Test
    void arrivalOnTheNextDayKeepsTheDepartureDate() {
        SsmMessage ssm =
                ReplanningSsmMapper.toSsm(event("2026-11-11T22:30:00Z", "2026-11-12T01:10:00Z"));

        assertThat(ssm.startDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(ssm.departureTime()).isEqualTo(LocalTime.of(22, 30));
        assertThat(ssm.arrivalTime()).isEqualTo(LocalTime.of(1, 10));
    }

    static FlightReplannedMessage event(String newDeparture, String newArrival) {
        return new FlightReplannedMessage(
                "6f1c2a0e",
                1,
                "HJ500",
                LocalDate.of(2026, 11, 10),
                "BRU",
                "AMS",
                "SXBAA",
                "HJ500A",
                Instant.parse(newDeparture),
                Instant.parse(newArrival));
    }
}
