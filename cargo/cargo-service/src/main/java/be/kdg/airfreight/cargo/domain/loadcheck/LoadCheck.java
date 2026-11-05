package be.kdg.airfreight.cargo.domain.loadcheck;

import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import java.util.List;

// one rule on the whole load, a new check implements this and joins the list in LoadChecks
public interface LoadCheck {
    LoadCheckResult evaluate(List<UldBooking> bookings, AirportCode arrivalAirport);
}
