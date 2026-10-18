package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.math.BigDecimal;

/**
 * Incoming ULD booking message. The course has not published the official ULD JSON schema (Q01);
 * these field names are the team's provisional contract.
 */
record UldMessage(
        String messageId,
        String flightNumber,
        Integer scheduledDepartureDay,
        String departureAirportCode,
        String uldCode,
        WeightValue weight,
        String destination) {
    record WeightValue(BigDecimal value, String unit) {}
}
