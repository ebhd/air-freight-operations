package be.kdg.airfreight.cargo.api.flight;

import be.kdg.airfreight.cargo.application.cargoplan.CargoPlanView;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

// finalizedAt is null while the plan is a draft, movedTo is null unless the state is REPLANNED
public record CargoPlanResponse(
        FlightOverviewResponse flight,
        List<UldBookingResponse> bookings,
        String state,
        Instant finalizedAt,
        MovedToResponse movedTo) {
    static CargoPlanResponse from(CargoPlanView view) {
        List<UldBookingResponse> bookings =
                view.cargoPlan().getBookings().stream()
                        .map(UldBookingResponse::from)
                        // same row order on every load
                        .sorted(Comparator.comparing(UldBookingResponse::uldCode))
                        .toList();
        return new CargoPlanResponse(
                FlightOverviewResponse.from(view.flight()),
                bookings,
                view.cargoPlan().getState().name(),
                view.cargoPlan().getFinalizedAt().orElse(null),
                view.cargoPlan().getMovedTo().map(MovedToResponse::from).orElse(null));
    }
}
