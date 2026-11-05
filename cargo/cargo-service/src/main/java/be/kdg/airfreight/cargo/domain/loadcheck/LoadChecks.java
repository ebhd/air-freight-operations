package be.kdg.airfreight.cargo.domain.loadcheck;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import java.util.ArrayList;
import java.util.List;

// runs every check every time and never changes the plan
public final class LoadChecks {
    private final List<LoadCheck> checks;

    public LoadChecks(LateralBalanceCheck lateralBalance) {
        if (lateralBalance == null) {
            throw new InvalidCargoValueException("Lateral balance check is required");
        }
        checks = List.of(lateralBalance, new OffloadingCheck(), new AllCargoLoadedCheck());
    }

    public LoadCheckReport run(List<UldBooking> bookings, AirportCode arrivalAirport) {
        List<LoadCheckResult> results = new ArrayList<>();
        for (LoadCheck check : checks) {
            results.add(check.evaluate(bookings, arrivalAirport));
        }
        return new LoadCheckReport(results);
    }
}
