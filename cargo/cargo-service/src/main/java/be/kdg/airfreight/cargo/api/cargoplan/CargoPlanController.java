package be.kdg.airfreight.cargo.api.cargoplan;

import be.kdg.airfreight.cargo.api.CargoApiExceptionHandler;
import be.kdg.airfreight.cargo.api.CargoRequestMapper;
import be.kdg.airfreight.cargo.application.cargoplan.CargoPlanQueryService;
import be.kdg.airfreight.cargo.application.cargoplan.UldPositionService;
import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cargo-plans")
@Slf4j
public class CargoPlanController {
    private static final String POSITION_PATH =
            "/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}"
                    + "/bookings/{uldCode}/position";

    private final CargoPlanQueryService cargoPlanQueryService;
    private final UldPositionService uldPositionService;

    public CargoPlanController(
            CargoPlanQueryService cargoPlanQueryService, UldPositionService uldPositionService) {
        this.cargoPlanQueryService = cargoPlanQueryService;
        this.uldPositionService = uldPositionService;
    }

    // unknown flight 404 and flight down 503 come from CargoApiExceptionHandler
    @GetMapping("/{flightNumber}/{scheduledDepartureDate}/{departureAirportCode}")
    public CargoPlanResponse findCargoPlan(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode) {
        FlightKey flightKey =
                CargoRequestMapper.toFlightKey(
                        flightNumber, scheduledDepartureDate, departureAirportCode);
        return CargoPlanResponse.from(cargoPlanQueryService.getCargoPlan(flightKey));
    }

    // put replaces the position so sending it twice gives the same result
    @PutMapping(POSITION_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignPosition(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @PathVariable String uldCode,
            @RequestBody AssignPositionRequest request) {
        FlightKey flightKey =
                CargoRequestMapper.toFlightKey(
                        flightNumber, scheduledDepartureDate, departureAirportCode);
        UldCode code = CargoRequestMapper.toUldCode(uldCode);
        LoadingPosition position = CargoRequestMapper.toPosition(request.position());
        uldPositionService.assignPosition(flightKey, code, position);
    }

    @DeleteMapping(POSITION_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearPosition(
            @PathVariable String flightNumber,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate scheduledDepartureDate,
            @PathVariable String departureAirportCode,
            @PathVariable String uldCode) {
        FlightKey flightKey =
                CargoRequestMapper.toFlightKey(
                        flightNumber, scheduledDepartureDate, departureAirportCode);
        uldPositionService.clearPosition(flightKey, CargoRequestMapper.toUldCode(uldCode));
    }

    // here only the unique constraint on the position fails so it is a concurrent change
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail concurrentPositionChange(DataIntegrityViolationException exception) {
        log.warn("Concurrent change on cargo plan refused: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, CargoApiExceptionHandler.CONCURRENT_CHANGE);
    }
}
