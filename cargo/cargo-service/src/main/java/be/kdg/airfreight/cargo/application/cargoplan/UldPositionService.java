package be.kdg.airfreight.cargo.application.cargoplan;

import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldBooking;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.cargoplan.UldNotBookedException;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
@Slf4j
public class UldPositionService {
    private final CargoPlanRepository cargoPlanRepository;
    private final LoadingWindowService loadingWindowService;
    private final TransactionOperations transaction;

    public UldPositionService(
            CargoPlanRepository cargoPlanRepository,
            LoadingWindowService loadingWindowService,
            TransactionOperations transaction) {
        this.cargoPlanRepository = cargoPlanRepository;
        this.loadingWindowService = loadingWindowService;
        this.transaction = transaction;
    }

    public void assignPosition(FlightKey flightKey, UldCode uldCode, LoadingPosition position) {
        // flight is asked over http before the transaction so no db connection waits on it
        LoadingContext context = loadingWindowService.contextFor(flightKey);
        // load change and save in one transaction so save checks the version that was loaded
        transaction.executeWithoutResult(
                status -> {
                    CargoPlan cargoPlan = requirePlan(flightKey, uldCode);
                    Optional<LoadingPosition> before = positionOf(cargoPlan, uldCode);
                    cargoPlan.assignPosition(uldCode, position, context);
                    cargoPlanRepository.save(cargoPlan);
                    logAssignment(flightKey, uldCode, before, position);
                });
    }

    public void clearPosition(FlightKey flightKey, UldCode uldCode) {
        LoadingContext context = loadingWindowService.contextFor(flightKey);
        transaction.executeWithoutResult(
                status -> {
                    CargoPlan cargoPlan = requirePlan(flightKey, uldCode);
                    Optional<LoadingPosition> before = positionOf(cargoPlan, uldCode);
                    cargoPlan.clearPosition(uldCode, context);
                    cargoPlanRepository.save(cargoPlan);
                    if (before.isPresent()) {
                        log.info(
                                "Cleared position {} of ULD {} on flight {}",
                                before.get(),
                                uldCode.value(),
                                flightKey);
                    } else {
                        log.debug(
                                "ULD {} on flight {} had no position to clear",
                                uldCode.value(),
                                flightKey);
                    }
                });
    }

    // a flight without a stored plan has no bookings at all
    private CargoPlan requirePlan(FlightKey flightKey, UldCode uldCode) {
        return cargoPlanRepository
                .findByFlightKey(flightKey)
                .orElseThrow(() -> new UldNotBookedException(flightKey, uldCode));
    }

    private static Optional<LoadingPosition> positionOf(CargoPlan cargoPlan, UldCode uldCode) {
        return cargoPlan.findBooking(uldCode).flatMap(UldBooking::getPosition);
    }

    // logged before the commit so a refused commit adds a warning from the 409 handler
    private static void logAssignment(
            FlightKey flightKey,
            UldCode uldCode,
            Optional<LoadingPosition> before,
            LoadingPosition position) {
        if (before.isEmpty()) {
            log.info(
                    "Assigned ULD {} to position {} on flight {}",
                    uldCode.value(),
                    position,
                    flightKey);
        } else if (before.get() != position) {
            log.info(
                    "Moved ULD {} from position {} to {} on flight {}",
                    uldCode.value(),
                    before.get(),
                    position,
                    flightKey);
        } else {
            log.debug(
                    "ULD {} stays on position {} on flight {}",
                    uldCode.value(),
                    position,
                    flightKey);
        }
    }
}
