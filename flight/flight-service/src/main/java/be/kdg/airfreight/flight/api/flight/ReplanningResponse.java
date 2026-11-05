package be.kdg.airfreight.flight.api.flight;

import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.replanning.Replanning;
import java.time.Instant;
import java.time.LocalDate;

// the new schedule of a replanned flight and the delay change that caused it
public record ReplanningResponse(
        String newFlightNumber,
        LocalDate newScheduledDepartureDate,
        Instant newScheduledDeparture,
        Instant newScheduledArrival,
        boolean replacementCreated,
        Instant replannedAt,
        String delayType,
        int minutes,
        String reasonCode,
        String description) {
    static ReplanningResponse from(Flight flight, Replanning replanning) {
        return new ReplanningResponse(
                replanning.newFlightNumber(),
                replanning.newScheduledDepartureDate(),
                replanning.newScheduledDeparture(),
                replanning.newScheduledArrival(),
                flight.replacementCreated(),
                replanning.replannedAt(),
                replanning.delayType().name(),
                replanning.minutes(),
                replanning.reasonCode(),
                replanning.description());
    }
}
