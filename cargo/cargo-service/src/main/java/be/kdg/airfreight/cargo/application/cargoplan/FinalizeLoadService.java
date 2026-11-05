package be.kdg.airfreight.cargo.application.cargoplan;

import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadFinalized;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadChecks;
import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
@Slf4j
public class FinalizeLoadService {
    private final CargoPlanRepository cargoPlanRepository;
    private final LoadingWindowService loadingWindowService;
    private final LoadChecks loadChecks;
    private final ApplicationEventPublisher events;
    private final TransactionOperations transaction;

    public FinalizeLoadService(
            CargoPlanRepository cargoPlanRepository,
            LoadingWindowService loadingWindowService,
            LoadChecks loadChecks,
            ApplicationEventPublisher events,
            TransactionOperations transaction) {
        this.cargoPlanRepository = cargoPlanRepository;
        this.loadingWindowService = loadingWindowService;
        this.loadChecks = loadChecks;
        this.events = events;
        this.transaction = transaction;
    }

    public LoadFinalized finalizeLoad(FlightKey flightKey) {
        // flight is asked before the transaction so no db connection waits on http
        LoadingContext context = loadingWindowService.contextFor(flightKey);
        // plan and outbox row commit together or not at all
        return transaction.execute(
                status -> {
                    // a flight without bookings can be finalized with an empty load
                    CargoPlan cargoPlan =
                            cargoPlanRepository
                                    .findByFlightKey(flightKey)
                                    .orElseGet(() -> new CargoPlan(flightKey));
                    LoadFinalized event = cargoPlan.finalizeLoad(context, loadChecks);
                    cargoPlanRepository.save(cargoPlan);
                    events.publishEvent(event);
                    log.info(
                            "Finalized load of flight {} with {} positioned ULDs",
                            flightKey,
                            event.ulds().size());
                    return event;
                });
    }
}
