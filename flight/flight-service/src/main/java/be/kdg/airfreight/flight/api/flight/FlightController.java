package be.kdg.airfreight.flight.api.flight;

import be.kdg.airfreight.flight.application.flight.FlightQueryService;
import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flights")
public class FlightController {
    private final FlightQueryService flightQueryService;

    public FlightController(FlightQueryService flightQueryService) {
        this.flightQueryService = flightQueryService;
    }

    // day overview like /api/flights?date=2026-10-19
    @GetMapping
    public List<FlightOverviewResponse> findFlights(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return flightQueryService.findFlightsOn(date).stream()
                .map(FlightOverviewResponse::from)
                .toList();
    }

    @GetMapping("/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}")
    public ResponseEntity<FlightResponse> findFlight(
            @PathVariable String flightNumber,
            // iso so the url uses 2026-10-19
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode) {
        // the domain key validates the input
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        Flight flight =
                flightQueryService
                        .findFlight(key)
                        .orElseThrow(() -> new NotFoundException("Unknown flight " + key));
        return ResponseEntity.ok(FlightResponse.from(flight));
    }
}
