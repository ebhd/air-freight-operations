package be.kdg.airfreight.cargo.api.cargoplan;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.Weight;
import java.math.BigDecimal;

public record UldBookingResponse(
        String uldCode, WeightValue weight, String destination, String position) {
    public record WeightValue(BigDecimal value, String unit) {}

    static UldBookingResponse from(UldBooking booking) {
        Weight weight = booking.getWeight();
        return new UldBookingResponse(
                booking.getCode().value(),
                new WeightValue(weight.plainValue(), weight.unit().name()),
                booking.getDestination().value(),
                // null while the uld has no position
                booking.getPosition().map(LoadingPosition::name).orElse(null));
    }
}
