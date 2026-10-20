package be.kdg.airfreight.cargo.api;

import be.kdg.airfreight.cargo.application.FlightOverviewService;
import be.kdg.airfreight.cargo.domain.FlightLookupUnavailableException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return flightOverviewService.findFlightsOn(date).stream()
                .map(FlightOverviewResponse::from)
                .toList();
    }

    // flight down is a clear 503 and never a fake empty list
    @ExceptionHandler(FlightLookupUnavailableException.class)
    ProblemDetail flightOperationsUnavailable(FlightLookupUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Flight Operations is unavailable, try again later");
    }
}
