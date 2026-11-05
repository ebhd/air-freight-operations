package be.kdg.airfreight.cargo.infrastructure.messaging.uld;

import java.math.BigDecimal;

// version 1 of the uld message json
record UldMessage(
        String messageId,
        Integer version,
        String flightNumber,
        Integer scheduledDepartureDay,
        String departureAirportCode,
        String uldCode,
        WeightValue weight,
        String destination) {
    record WeightValue(BigDecimal value, String unit) {}
}
