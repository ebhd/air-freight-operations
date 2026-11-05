package be.kdg.airfreight.cargo.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// the published language of cargo.load-finalized, a field change needs version 2
record LoadFinalizedMessage(
        UUID messageId,
        int version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        List<Uld> ulds) {
    static final int VERSION = 1;

    // weight keeps the unit it was booked with
    record Uld(
            String uldCode, String position, WeightValue weight, String destinationAirportCode) {}

    record WeightValue(BigDecimal value, String unit) {}
}
