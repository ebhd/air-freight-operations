package be.kdg.airfreight.cargo.domain.cargoplan;

import be.kdg.airfreight.cargo.domain.flight.FlightKey;

// the uld is not booked on this flight so the api answers 404
public class UldNotBookedException extends RuntimeException {
    public UldNotBookedException(FlightKey flightKey, UldCode uldCode) {
        super("ULD " + uldCode.value() + " is not booked on flight " + flightKey);
    }
}
