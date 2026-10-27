package be.kdg.airfreight.cargo.api.flight;

import be.kdg.airfreight.cargo.application.flight.FlightOverviewService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flights")
public class FlightOverviewController {
    private final FlightOverviewService flightOverviewService;

    public FlightOverviewController(FlightOverviewService flightOverviewService) {
        this.flightOverviewService = flightOverviewService;
    }

    // day overview like /api/flights?date=2026-10-19
    @GetMapping
    public List<FlightOverviewResponse> findFlights(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean includeDeparted) {
        return flightOverviewService.findFlightsOn(date, includeDeparted).stream()
                .map(FlightOverviewResponse::from)
                .toList();
    }
}
