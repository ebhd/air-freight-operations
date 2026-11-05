package be.kdg.airfreight.flight.api.flight.delay;

import be.kdg.airfreight.flight.api.flight.FlightResponse;
import be.kdg.airfreight.flight.application.delay.FlightDelayService;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flights/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}")
public class FlightDelayController {
    private final FlightDelayService flightDelayService;

    public FlightDelayController(FlightDelayService flightDelayService) {
        this.flightDelayService = flightDelayService;
    }

    @PostMapping("/departure-delays")
    public ResponseEntity<FlightResponse> addDepartureDelay(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @RequestBody AddDelayRequest request) {
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        FlightResponse response =
                FlightResponse.from(flightDelayService.addDepartureDelay(request.toCommand(key)));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/departure-delays/{delayId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateDepartureDelay(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @PathVariable UUID delayId,
            @RequestBody UpdateDelayRequest request) {
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        flightDelayService.updateDepartureDelay(request.toCommand(key, delayId));
    }

    @DeleteMapping("/departure-delays/{delayId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDepartureDelay(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @PathVariable UUID delayId) {
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        flightDelayService.removeDepartureDelay(key, delayId);
    }

    @PostMapping("/arrival-delays")
    public ResponseEntity<FlightResponse> addArrivalDelay(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @RequestBody AddDelayRequest request) {
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        FlightResponse response =
                FlightResponse.from(flightDelayService.addArrivalDelay(request.toCommand(key)));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/arrival-delays/{delayId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateArrivalDelay(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @PathVariable UUID delayId,
            @RequestBody UpdateDelayRequest request) {
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        flightDelayService.updateArrivalDelay(request.toCommand(key, delayId));
    }

    @DeleteMapping("/arrival-delays/{delayId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeArrivalDelay(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @PathVariable UUID delayId) {
        FlightKey key = new FlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        flightDelayService.removeArrivalDelay(key, delayId);
    }
}
