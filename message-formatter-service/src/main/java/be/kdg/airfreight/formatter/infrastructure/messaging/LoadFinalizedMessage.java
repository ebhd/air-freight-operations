package be.kdg.airfreight.formatter.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// our copy of cargo's cargo.load-finalized version 1
public record LoadFinalizedMessage(
        UUID messageId,
        int version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        List<Uld> ulds) {
    public static final int VERSION = 1;

    public record Uld(
            String uldCode, String position, Weight weight, String destinationAirportCode) {}

    public record Weight(BigDecimal value, String unit) {}
}
