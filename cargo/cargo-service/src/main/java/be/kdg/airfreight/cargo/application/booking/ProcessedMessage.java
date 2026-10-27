package be.kdg.airfreight.cargo.application.booking;

import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import java.time.Instant;

// a redelivery reuses this instead of resolving the date again
public record ProcessedMessage(
        String messageId,
        Instant processedAt,
        FlightKey flightKey,
        UldCode uldCode,
        BookUldResult.Outcome outcome) {}
