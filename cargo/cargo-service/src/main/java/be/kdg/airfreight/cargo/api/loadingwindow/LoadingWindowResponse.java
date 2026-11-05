package be.kdg.airfreight.cargo.api.loadingwindow;

import be.kdg.airfreight.cargo.application.loading.LoadingStatus;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import java.time.Instant;
import java.time.LocalDate;

// instants in utc and the page shows them in brussels time
public record LoadingWindowResponse(
        String flightNumber,
        LocalDate scheduledDepartureDate,
        String departureAirportCode,
        String flightStatus,
        Instant opensAt,
        Instant closesAt,
        boolean finalized,
        Instant checkedAt,
        boolean loadingAllowed,
        String refusal) {
    static LoadingWindowResponse from(LoadingStatus status) {
        FlightInformation flight = status.context().flight();
        return new LoadingWindowResponse(
                flight.flightKey().flightNumber(),
                flight.flightKey().scheduledDepartureDate(),
                flight.flightKey().departureAirportCode().value(),
                flight.status().name(),
                status.context().window().opensAt(),
                status.context().window().closesAt(),
                status.finalized(),
                status.context().now(),
                status.loadingAllowed(),
                status.refusal() == null ? null : status.refusal().name());
    }
}
