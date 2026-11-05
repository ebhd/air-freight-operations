package be.kdg.airfreight.formatter.infrastructure.formatting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// the three lines every mvt shares, only the airport, the action code and the time differ
final class MvtTelegram {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd", Locale.ROOT);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HHmm", Locale.ROOT).withZone(ZoneOffset.UTC);

    private MvtTelegram() {}

    // line 2 keeps the scheduled departure day, line 3 is the utc time without a date
    static String lines(
            String flightNumber,
            LocalDate scheduledDepartureDate,
            String aircraftRegistration,
            String airportCode,
            String actionCode,
            Instant time) {
        return "MVT\n"
                + flightNumber
                + "/"
                + DAY.format(scheduledDepartureDate)
                + "."
                + aircraftRegistration
                + "."
                + airportCode
                + "\n"
                + actionCode
                + TIME.format(time);
    }
}
