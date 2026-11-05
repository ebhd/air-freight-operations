package be.kdg.airfreight.flight.api.flight.actualtime;

import be.kdg.airfreight.flight.api.flight.FlightResponse;
import be.kdg.airfreight.flight.application.actualtime.FlightActualTimeService;
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
public class FlightActualTimeController {
    private final FlightActualTimeService flightActualTimeService;

    public FlightActualTimeController(FlightActualTimeService flightActualTimeService) {
        this.flightActualTimeService = flightActualTimeService;
    }

    // post because registering is a one-time command that changes the state, never a replace
    @PostMapping("/departure")
    public ResponseEntity<FlightResponse> recordDeparture(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @RequestBody RecordDepartureRequest request) {
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        FlightResponse response =
                FlightResponse.from(
                        flightActualTimeService.recordDeparture(request.toCommand(key)));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/arrival")
    public ResponseEntity<FlightResponse> recordArrival(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @RequestBody RecordArrivalRequest request) {
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        FlightResponse response =
                FlightResponse.from(flightActualTimeService.recordArrival(request.toCommand(key)));
        return ResponseEntity.ok(response);
    }
}
