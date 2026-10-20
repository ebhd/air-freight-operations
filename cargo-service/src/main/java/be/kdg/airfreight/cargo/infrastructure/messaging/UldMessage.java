package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.math.BigDecimal;

// our provisional json until the course publishes the uld schema q01
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
