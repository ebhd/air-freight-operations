package be.kdg.airfreight.cargo.domain.loadcheck;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.offloading.OffloadingCheck;
import be.kdg.airfreight.cargo.domain.offloading.OffloadingResult;
import java.util.List;
import java.util.stream.Collectors;

// runs all three checks every time and never changes the plan
public final class LoadChecks {
    static final String OFFLOADING = "Offloading";

    private final LateralBalanceCheck lateralBalance;
    private final OffloadingCheck offloading = new OffloadingCheck();
    private final AllCargoLoadedCheck allCargoLoaded = new AllCargoLoadedCheck();

    public LoadChecks(LateralBalanceCheck lateralBalance) {
        if (lateralBalance == null) {
            throw new InvalidCargoValueException("Lateral balance check is required");
        }
        this.lateralBalance = lateralBalance;
    }

    public LoadCheckReport run(List<UldBooking> bookings, AirportCode arrivalAirport) {
        return new LoadCheckReport(
                List.of(
                        lateralBalance.evaluate(bookings),
                        offloading(bookings, arrivalAirport),
                        allCargoLoaded.evaluate(bookings)));
    }

    private LoadCheckResult offloading(List<UldBooking> bookings, AirportCode arrivalAirport) {
        OffloadingResult result = offloading.evaluate(bookings, arrivalAirport);
        if (result.passed()) {
            return new LoadCheckResult(
                    OFFLOADING,
                    CheckSeverity.HARD,
                    true,
                    "All cargo for " + arrivalAirport + " can leave without moving other cargo");
        }
        String blocked =
                result.blockedPositions().stream()
                        .map(LoadingPosition::name)
                        .collect(Collectors.joining(", "));
        return new LoadCheckResult(
                OFFLOADING,
                CheckSeverity.HARD,
                false,
                "Cargo for "
                        + arrivalAirport
                        + " at "
                        + blocked
                        + " cannot leave without moving cargo for other airports");
    }
}
