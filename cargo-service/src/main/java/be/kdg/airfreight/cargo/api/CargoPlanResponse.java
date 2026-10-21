package be.kdg.airfreight.cargo.api;

import be.kdg.airfreight.cargo.application.CargoPlanView;
import java.util.Comparator;
import java.util.List;

public record CargoPlanResponse(FlightOverviewResponse flight, List<UldBookingResponse> bookings) {
    static CargoPlanResponse from(CargoPlanView view) {
        List<UldBookingResponse> bookings =
                view.cargoPlan().getBookings().stream()
                        .map(UldBookingResponse::from)
                        // same row order on every load
                        .sorted(Comparator.comparing(UldBookingResponse::uldCode))
                        .toList();
        return new CargoPlanResponse(FlightOverviewResponse.from(view.flight()), bookings);
    }
}
