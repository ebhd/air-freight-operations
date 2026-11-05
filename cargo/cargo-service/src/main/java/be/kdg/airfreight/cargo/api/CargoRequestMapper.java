package be.kdg.airfreight.cargo.api;

import be.kdg.airfreight.cargo.domain.cargoplan.LoadingPosition;
import be.kdg.airfreight.cargo.domain.cargoplan.UldCode;
import be.kdg.airfreight.cargo.domain.exception.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// only bad input in the url or body is a 400 so bad data from flight stays a 500
@Slf4j
public final class CargoRequestMapper {
    private CargoRequestMapper() {}

    public static FlightKey toFlightKey(
            String flightNumber, LocalDate scheduledDepartureDate, String departureAirportCode) {
        try {
            return new FlightKey(
                    flightNumber, scheduledDepartureDate, new AirportCode(departureAirportCode));
        } catch (InvalidCargoValueException invalid) {
            throw badRequest(invalid);
        }
    }

    public static UldCode toUldCode(String uldCode) {
        try {
            return new UldCode(uldCode);
        } catch (InvalidCargoValueException invalid) {
            throw badRequest(invalid);
        }
    }

    public static LoadingPosition toPosition(String position) {
        try {
            return LoadingPosition.fromCode(position);
        } catch (InvalidCargoValueException invalid) {
            throw badRequest(invalid);
        }
    }

    private static ResponseStatusException badRequest(InvalidCargoValueException invalid) {
        log.info("Invalid cargo plan request: {}", invalid.getMessage());
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
    }
}
