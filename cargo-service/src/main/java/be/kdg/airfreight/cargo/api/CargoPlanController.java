package be.kdg.airfreight.cargo.api;

import be.kdg.airfreight.cargo.application.CargoPlanQueryService;
import be.kdg.airfreight.cargo.domain.AirportCode;
import be.kdg.airfreight.cargo.domain.FlightKey;
import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cargo-plans")
@Slf4j
public class CargoPlanController {
    private final CargoPlanQueryService cargoPlanQueryService;

    public CargoPlanController(CargoPlanQueryService cargoPlanQueryService) {
        this.cargoPlanQueryService = cargoPlanQueryService;
    }

    // unknown flight 404 and flight down 503 come from CargoApiExceptionHandler
    @GetMapping("/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}")
    public CargoPlanResponse findCargoPlan(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode) {
        FlightKey flightKey =
                toFlightKey(flightNumber, scheduledDepartureDate, departureAirportCode);
        return CargoPlanResponse.from(cargoPlanQueryService.getCargoPlan(flightKey));
    }

    // only a bad key in the url is a 400 so bad data from flight stays a 500
    private static FlightKey toFlightKey(
            String flightNumber, LocalDate scheduledDepartureDate, String departureAirportCode) {
        try {
            return new FlightKey(
                    flightNumber, scheduledDepartureDate, new AirportCode(departureAirportCode));
        } catch (InvalidCargoValueException invalid) {
            log.info("Invalid cargo plan request: {}", invalid.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        }
    }
}
