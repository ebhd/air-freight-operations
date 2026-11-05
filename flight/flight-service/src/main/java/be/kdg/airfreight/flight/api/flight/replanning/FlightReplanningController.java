package be.kdg.airfreight.flight.api.flight.replanning;

import be.kdg.airfreight.flight.api.flight.FlightResponse;
import be.kdg.airfreight.flight.application.replanning.FlightReplanningService;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flights/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}")
public class FlightReplanningController {
    private final FlightReplanningService flightReplanningService;

    public FlightReplanningController(FlightReplanningService flightReplanningService) {
        this.flightReplanningService = flightReplanningService;
    }

    // ends the flight and asks the planning system for the new one; 200 with the old flight
    // because the new flight only exists once its ssm is processed
    @PostMapping("/replanning")
    public ResponseEntity<FlightResponse> replan(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @RequestBody ReplanFlightRequest request) {
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        FlightResponse response =
                FlightResponse.from(flightReplanningService.replan(request.toCommand(key)));
        return ResponseEntity.ok(response);
    }
}
