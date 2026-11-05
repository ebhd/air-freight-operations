package be.kdg.airfreight.cargo.api.flight;

import be.kdg.airfreight.cargo.application.cargoplan.FinalizeLoadService;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cargo-plans/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}")
public class FinalizationController {
    private final FinalizeLoadService finalizeLoadService;

    public FinalizationController(FinalizeLoadService finalizeLoadService) {
        this.finalizeLoadService = finalizeLoadService;
    }

    // a post because it changes the plan for good, a second post gets 409 plan finalized
    @PostMapping("/finalization")
    public FinalizationResponse finalizeLoad(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode) {
        FlightKey flightKey =
                FlightKeyRequestMapper.toFlightKey(
                        flightNumber, scheduledDepartureDate, departureAirportCode);
        return FinalizationResponse.from(finalizeLoadService.finalizeLoad(flightKey));
    }
}
