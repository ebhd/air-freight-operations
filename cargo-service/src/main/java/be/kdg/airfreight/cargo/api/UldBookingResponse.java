package be.kdg.airfreight.cargo.api;

import be.kdg.airfreight.cargo.domain.UldBooking;
import be.kdg.airfreight.cargo.domain.Weight;
import java.math.BigDecimal;

public record UldBookingResponse(
        String uldCode, WeightValue weight, String destination, String position) {
    public record WeightValue(BigDecimal value, String unit) {}

    static UldBookingResponse from(UldBooking booking) {
        Weight weight = booking.getWeight();
        return new UldBookingResponse(
                booking.getCode().value(),
                // 1E+3 becomes 1000 in the json
                new WeightValue(
                        new BigDecimal(weight.value().toPlainString()), weight.unit().name()),
                booking.getDestination().value(),
                // stays null until issue 20 adds positions
                null);
    }
}
