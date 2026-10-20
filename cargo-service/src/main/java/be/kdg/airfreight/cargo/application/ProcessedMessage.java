package be.kdg.airfreight.cargo.application;

import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.UldCode;
import java.time.Instant;

// a redelivery reuses this instead of resolving the date again
public record ProcessedMessage(
        String messageId,
        Instant processedAt,
        FlightKey flightKey,
        UldCode uldCode,
        BookUldResult.Outcome outcome) {}
