package be.kdg.airfreight.flight.api;

import be.kdg.airfreight.flight.application.FlightQueryService;
import be.kdg.airfreight.flight.domain.FlightKey;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flights")
public class FlightController {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlightController.class);

    private final FlightQueryService flightQueryService;

    public FlightController(FlightQueryService flightQueryService) {
        this.flightQueryService = flightQueryService;
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
        return flightQueryService
                .findFlight(key)
                .map(flight -> ResponseEntity.ok(FlightResponse.from(flight)))
                .orElseGet(
                        () -> {
                            LOGGER.info("Flight lookup for unknown flight {}", key);
                            return ResponseEntity.notFound().build();
                        });
    }

    // invalid key becomes 400 instead of 500
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidFlightKey(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
