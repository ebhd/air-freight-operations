package be.kdg.airfreight.cargo.application.cargoplan;

import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadFinalized;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadChecks;
import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
@Slf4j
public class FinalizeLoadService {
    private final CargoPlanRepository cargoPlanRepository;
    private final LoadingWindowService loadingWindowService;
    private final LoadChecks loadChecks;
    private final CargoEventOutbox outbox;
    private final TransactionOperations transaction;

    public FinalizeLoadService(
            CargoPlanRepository cargoPlanRepository,
            LoadingWindowService loadingWindowService,
            LoadChecks loadChecks,
            CargoEventOutbox outbox,
            TransactionOperations transaction) {
        this.cargoPlanRepository = cargoPlanRepository;
        this.loadingWindowService = loadingWindowService;
        this.loadChecks = loadChecks;
        this.outbox = outbox;
        this.transaction = transaction;
    }

    public LoadFinalized finalizeLoad(FlightKey flightKey) {
        // flight is asked before the transaction so no db connection waits on http
        LoadingContext context = loadingWindowService.contextFor(flightKey);
        // plan and outbox row commit together or not at all
        return transaction.execute(
                status -> {
                    // a flight without bookings can be finalized with an empty load (d3)
                    CargoPlan cargoPlan =
                            cargoPlanRepository
                                    .findByFlightKey(flightKey)
                                    .orElseGet(() -> new CargoPlan(flightKey));
                    LoadFinalized event = cargoPlan.finalizeLoad(context, loadChecks);
                    cargoPlanRepository.save(cargoPlan);
                    outbox.add(event);
                    log.info(
                            "Finalized load of flight {} with {} positioned ULDs",
                            flightKey,
                            event.ulds().size());
                    return event;
                });
    }
}
