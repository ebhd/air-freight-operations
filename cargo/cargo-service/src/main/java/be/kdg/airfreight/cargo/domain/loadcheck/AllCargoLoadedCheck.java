package be.kdg.airfreight.cargo.domain.loadcheck;

import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import java.util.List;

// soft check: a booking without a position stays behind
public final class AllCargoLoadedCheck implements LoadCheck {
    static final String NAME = "Not all cargo loaded";

    @Override
    public LoadCheckResult evaluate(List<UldBooking> bookings, AirportCode arrivalAirport) {
        List<String> left =
                bookings.stream()
                        .filter(booking -> booking.getPosition().isEmpty())
                        .map(booking -> booking.getCode().value())
                        .sorted()
                        .toList();
        if (left.isEmpty()) {
            return new LoadCheckResult(
                    NAME, CheckSeverity.SOFT, true, "Every booked ULD has a position");
        }
        String count = left.size() == 1 ? "1 booked ULD has" : left.size() + " booked ULDs have";
        return new LoadCheckResult(
                NAME,
                CheckSeverity.SOFT,
                false,
                count + " no position and will stay behind: " + String.join(", ", left));
    }
}
