package be.kdg.airfreight.cargo.application;

import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.UldCode;
import java.time.Instant;

/**
 * A ULD message that was applied, with the flight it was resolved to the first time. A redelivery
 * reuses this result instead of resolving the day of month again.
 */
public record ProcessedMessage(
        String messageId,
        Instant processedAt,
        FlightKey flightKey,
        UldCode uldCode,
        BookUldResult.Outcome outcome) {}
