package be.kdg.airfreight.partner.infrastructure.formatting;

import static org.assertj.core.api.Assertions.assertThat;

import be.kdg.airfreight.partner.domain.FlightEstimatesChangedFact;
import be.kdg.airfreight.partner.domain.MessageContent;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MvtFormatterTest {
    private final MvtEdFormatter edFormatter = new MvtEdFormatter();
    private final MvtEaFormatter eaFormatter = new MvtEaFormatter();

    @Test
    void edUsesTheDepartureAirportAndTheUtcDepartureTime() {
        MessageContent ed =
                edFormatter.format(
                        fact(
                                Instant.parse("2026-10-09T07:15:00Z"),
                                Instant.parse("2026-10-09T09:15:00Z")));

        assertThat(ed.type()).isEqualTo("MVT_ED");
        assertThat(ed.formatVersion()).isEqualTo(1);
        assertThat(ed.payload()).isEqualTo("MVT\nHJ221/09.SXBAA.BRU\nED0715");
    }

    @Test
    void eaUsesTheArrivalAirportAndTheUtcArrivalTime() {
        MessageContent ea =
                eaFormatter.format(
                        fact(
                                Instant.parse("2026-10-09T07:15:00Z"),
                                Instant.parse("2026-10-09T09:15:00Z")));

        assertThat(ea.type()).isEqualTo("MVT_EA");
        assertThat(ea.formatVersion()).isEqualTo(1);
        assertThat(ea.payload()).isEqualTo("MVT\nHJ221/09.SXBAA.AMS\nEA0915");
    }

    // line 2 keeps the scheduled day while the time is already on the next utc day
    @Test
    void timeAfterMidnightKeepsTheScheduledDay() {
        FlightEstimatesChangedFact late =
                fact(Instant.parse("2026-10-10T00:15:00Z"), Instant.parse("2026-10-10T02:05:00Z"));

        assertThat(edFormatter.format(late).payload()).isEqualTo("MVT\nHJ221/09.SXBAA.BRU\nED0015");
        assertThat(eaFormatter.format(late).payload()).isEqualTo("MVT\nHJ221/09.SXBAA.AMS\nEA0205");
    }

    private static FlightEstimatesChangedFact fact(
            Instant estimatedDeparture, Instant estimatedArrival) {
        return new FlightEstimatesChangedFact(
                "HJ221",
                LocalDate.of(2026, 10, 9),
                "BRU",
                "AMS",
                "SXBAA",
                Instant.parse("2026-10-09T06:45:00Z"),
                Instant.parse("2026-10-09T08:45:00Z"),
                estimatedDeparture,
                estimatedArrival,
                Instant.parse("2026-10-09T05:00:00Z"));
    }
}
