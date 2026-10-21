package be.kdg.airfreight.cargo.api;

import be.kdg.airfreight.cargo.application.UnknownFlightException;
import be.kdg.airfreight.cargo.domain.FlightLookupUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// one answer per failure for every cargo endpoint
@RestControllerAdvice
class CargoApiExceptionHandler {
    // flight down is a clear 503 and never a fake empty list
    @ExceptionHandler(FlightLookupUnavailableException.class)
    ProblemDetail flightOperationsUnavailable(FlightLookupUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Flight Operations is unavailable, try again later");
    }

    @ExceptionHandler(UnknownFlightException.class)
    ProblemDetail unknownFlight(UnknownFlightException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
