package be.kdg.airfreight.cargo.application.loading;

import be.kdg.airfreight.cargo.application.flight.UnknownFlightException;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlan;
import be.kdg.airfreight.cargo.domain.cargoplan.CargoPlanRepository;
import be.kdg.airfreight.cargo.domain.flight.FlightInformation;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import be.kdg.airfreight.cargo.domain.flight.FlightRepository;
import be.kdg.airfreight.cargo.domain.loading.LoadingContext;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindow;
import be.kdg.airfreight.cargo.domain.loading.LoadingWindowPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoadingWindowService {
    private final FlightRepository flightRepository;
    private final CargoPlanRepository cargoPlanRepository;
    private final LoadingWindowPolicy policy;
    private final Clock clock;

    public LoadingWindowService(
            FlightRepository flightRepository,
            CargoPlanRepository cargoPlanRepository,
            LoadingWindowPolicy policy,
            Clock clock) {
        this.flightRepository = flightRepository;
        this.cargoPlanRepository = cargoPlanRepository;
        this.policy = policy;
        this.clock = clock;
    }

    // the copy follows flights estimate events so the latest etd sets the end of the window
    public LoadingContext contextFor(FlightKey flightKey) {
        Optional<FlightInformation> flight = flightRepository.findByFlightKey(flightKey);
        if (flight.isEmpty()) {
            log.info("Loading window requested for unknown flight {}", flightKey);
            throw new UnknownFlightException(flightKey);
        }
        LoadingWindow window =
                policy.windowFor(
                        flight.get().scheduledDeparture(), flight.get().estimatedDeparture());
        Instant now = clock.instant();
        log.debug(
                "Loading window for {} is {} to {} (UTC), now {}",
                flightKey,
                window.opensAt(),
                window.closesAt(),
                now);
        return new LoadingContext(flight.get(), window, now);
    }

    // reading never refuses so the refusal is returned as data
    public LoadingStatus statusFor(FlightKey flightKey) {
        LoadingContext context = contextFor(flightKey);
        CargoPlan cargoPlan =
                cargoPlanRepository
                        .findByFlightKey(flightKey)
                        .orElseGet(() -> new CargoPlan(flightKey));
        return new LoadingStatus(
                context, cargoPlan.isFinalized(), cargoPlan.loadingRefusal(context).orElse(null));
    }
}
