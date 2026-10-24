package be.kdg.airfreight.flight.api.flight;

import be.kdg.airfreight.flight.application.FlightDelayService;
import be.kdg.airfreight.flight.application.FlightQueryService;
import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flights")
public class FlightController {
    private final FlightQueryService flightQueryService;
    private final FlightDelayService flightDelayService;

    public FlightController(
            FlightQueryService flightQueryService, FlightDelayService flightDelayService) {
        this.flightQueryService = flightQueryService;
        this.flightDelayService = flightDelayService;
    }

    @PostMapping("/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/delays")
    public ResponseEntity<FlightResponse> addDelay(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @Valid @RequestBody AddDelayRequest request) {
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        FlightResponse response =
                FlightResponse.from(flightDelayService.addDelay(request.toCommand(key)));
        return ResponseEntity.ok(response);
    }

    @PatchMapping(
            "/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/delays/{delayId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateDelay(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @PathVariable UUID delayId,
            @Valid @RequestBody UpdateDelayRequest request) {
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        flightDelayService.updateDelay(request.toCommand(key, delayId));
    }

    @DeleteMapping(
            "/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/delays/{delayId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDelay(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @PathVariable UUID delayId) {
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        flightDelayService.removeDelay(key, delayId);
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
