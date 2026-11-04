package be.kdg.airfreight.flight.api.flight;

import be.kdg.airfreight.flight.application.FlightActualTimeService;
import be.kdg.airfreight.flight.application.FlightDelayService;
import be.kdg.airfreight.flight.application.FlightQueryService;
import be.kdg.airfreight.flight.application.FlightReplanningService;
import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.FlightKey;
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
    private final FlightActualTimeService flightActualTimeService;
    private final FlightReplanningService flightReplanningService;

    public FlightController(
            FlightQueryService flightQueryService,
            FlightDelayService flightDelayService,
            FlightActualTimeService flightActualTimeService,
            FlightReplanningService flightReplanningService) {
        this.flightQueryService = flightQueryService;
        this.flightDelayService = flightDelayService;
        this.flightActualTimeService = flightActualTimeService;
        this.flightReplanningService = flightReplanningService;
    }

    // post because registering is a one-time command that changes the state, never a replace
    @PostMapping("/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/departure")
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

    @PostMapping("/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/arrival")
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

    // ends the flight and asks the planning system for the new one; 200 with the old flight
    // because the new flight only exists once its ssm is processed
    @PostMapping("/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/replanning")
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

    @PostMapping("/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/departure-delays")
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

    @PatchMapping(
            "/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/departure-delays/{delayId}")
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

    @DeleteMapping(
            "/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/departure-delays/{delayId}")
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

    @PostMapping("/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/arrival-delays")
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

    @PatchMapping(
            "/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/arrival-delays/{delayId}")
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

    @DeleteMapping(
            "/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}/arrival-delays/{delayId}")
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
