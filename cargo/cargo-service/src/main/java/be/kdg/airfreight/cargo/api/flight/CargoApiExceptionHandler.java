package be.kdg.airfreight.cargo.api.flight;

import be.kdg.airfreight.cargo.application.flight.UnknownFlightException;
import be.kdg.airfreight.cargo.domain.cargoplan.PositionOccupiedException;
import be.kdg.airfreight.cargo.domain.cargoplan.UldNotBookedException;
import be.kdg.airfreight.cargo.domain.flight.FlightLookupUnavailableException;
import be.kdg.airfreight.cargo.domain.loading.LoadingNotAllowedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// one answer per failure for every cargo endpoint
@RestControllerAdvice
@Slf4j
class CargoApiExceptionHandler {
    static final String CONCURRENT_CHANGE =
            "The cargo plan was changed by someone else at the same time. Reload and try again.";

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

    // the request is valid but the plan or the moment does not allow it so 409 and not 403
    @ExceptionHandler(LoadingNotAllowedException.class)
    ProblemDetail loadingNotAllowed(LoadingNotAllowedException exception) {
        log.info(
                "Refused loading action on {}: {}",
                exception.getFlightKey(),
                exception.getRefusal());
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        // the page shows the times in brussels time so it needs them apart from the text
        problem.setProperty("refusal", exception.getRefusal());
        problem.setProperty("opensAt", exception.getWindow().opensAt());
        problem.setProperty("closesAt", exception.getWindow().closesAt());
        return problem;
    }

    @ExceptionHandler(UldNotBookedException.class)
    ProblemDetail uldNotBooked(UldNotBookedException exception) {
        log.warn("Position change for ULD not booked: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(PositionOccupiedException.class)
    ProblemDetail positionOccupied(PositionOccupiedException exception) {
        log.info("Refused position change: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    // another change to the same plan committed first so its version moved on
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentChange(OptimisticLockingFailureException exception) {
        log.warn("Concurrent change on cargo plan refused: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, CONCURRENT_CHANGE);
    }
}
