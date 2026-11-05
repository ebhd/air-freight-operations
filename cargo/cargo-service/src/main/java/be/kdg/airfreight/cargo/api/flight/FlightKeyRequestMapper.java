package be.kdg.airfreight.cargo.api.flight;

import be.kdg.airfreight.cargo.domain.InvalidCargoValueException;
import be.kdg.airfreight.cargo.domain.flight.AirportCode;
import be.kdg.airfreight.cargo.domain.flight.FlightKey;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
final class FlightKeyRequestMapper {
    private FlightKeyRequestMapper() {}

    static FlightKey toFlightKey(
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
