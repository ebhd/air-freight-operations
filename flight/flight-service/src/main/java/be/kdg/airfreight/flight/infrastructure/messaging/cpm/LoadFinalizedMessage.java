package be.kdg.airfreight.flight.infrastructure.messaging.cpm;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// our copy of cargo's cargo.load-finalized version 1, the ulds are left out because
// the departure gate only needs the fact that the load of this flight is final
public record LoadFinalizedMessage(
        UUID messageId,
        int version,
        Instant occurredOn,
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode) {
    public static final int VERSION = 1;
}
