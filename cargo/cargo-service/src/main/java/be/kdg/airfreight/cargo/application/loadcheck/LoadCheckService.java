package be.kdg.airfreight.cargo.application.loadcheck;

import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadCheckReport;
import be.kdg.airfreight.cargo.domain.loadcheck.LoadChecks;
import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoadCheckService {
    private final CargoPlanRepository cargoPlanRepository;
    private final LoadingWindowService loadingWindowService;
    private final LoadChecks loadChecks;

    public LoadCheckService(
            CargoPlanRepository cargoPlanRepository,
            LoadingWindowService loadingWindowService,
            LoadChecks loadChecks) {
        this.cargoPlanRepository = cargoPlanRepository;
        this.loadingWindowService = loadingWindowService;
        this.loadChecks = loadChecks;
    }

    // reads only so no transaction and nothing is saved
    public LoadCheckReport checkLoad(FlightKey flightKey) {
        LoadingContext context = loadingWindowService.contextFor(flightKey);
        // a known flight without bookings is an empty load
        CargoPlan cargoPlan =
                cargoPlanRepository
                        .findByFlightKey(flightKey)
                        .orElseGet(() -> new CargoPlan(flightKey));
        LoadCheckReport report = cargoPlan.checkLoad(context, loadChecks);
        log.info(
                "Checked load of flight {}: {}",
                flightKey,
                report.hasHardFailure() ? "hard check failed" : "hard checks passed");
        return report;
    }
}
