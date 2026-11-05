package be.kdg.airfreight.formatter.infrastructure.formatting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// the partner cpm json, partner adds the message id, creation time and format version
record CpmPayload(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        List<Uld> ulds) {
    record Uld(String uldCode, String position, Weight weight, String destinationAirportCode) {}

    record Weight(BigDecimal value, String unit) {}
}
