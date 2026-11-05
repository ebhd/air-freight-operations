package be.kdg.airfreight.cargo.api.flight;

import be.kdg.airfreight.cargo.application.loading.LoadingWindowService;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cargo-plans/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}")
public class LoadingWindowController {
    private final LoadingWindowService loadingWindowService;

    public LoadingWindowController(LoadingWindowService loadingWindowService) {
        this.loadingWindowService = loadingWindowService;
    }

    // always 200 for a known flight since a refusal is part of the answer
    @GetMapping("/loading-window")
    public LoadingWindowResponse findLoadingWindow(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode) {
        FlightKey flightKey =
                CargoPlanController.toFlightKey(
                        flightNumber, scheduledDepartureDate, departureAirportCode);
        return LoadingWindowResponse.from(loadingWindowService.statusFor(flightKey));
    }
}
