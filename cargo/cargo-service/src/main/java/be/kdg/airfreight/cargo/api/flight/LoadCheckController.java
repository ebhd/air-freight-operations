package be.kdg.airfreight.cargo.api.flight;

import be.kdg.airfreight.cargo.application.loadcheck.LoadCheckService;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cargo-plans/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}")
public class LoadCheckController {
    private final LoadCheckService loadCheckService;

    public LoadCheckController(LoadCheckService loadCheckService) {
        this.loadCheckService = loadCheckService;
    }

    // a get because checking never changes the load, 409 when the loading rules refuse it
    @GetMapping("/load-check")
    public LoadCheckReportResponse checkLoad(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode) {
        FlightKey flightKey =
                CargoPlanController.toFlightKey(
                        flightNumber, scheduledDepartureDate, departureAirportCode);
        return LoadCheckReportResponse.from(loadCheckService.checkLoad(flightKey));
    }
}
