package be.kdg.airfreight.flight.api;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;
import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.InvalidDelayException;
import be.kdg.airfreight.flight.domain.flight.InvalidScheduleException;
import be.kdg.airfreight.flight.domain.flight.TotalDelayExceededException;
import be.kdg.airfreight.flight.domain.flight.actualtime.ActualTimeRefusedException;
import be.kdg.airfreight.flight.domain.flight.actualtime.InvalidActualTimeException;
import be.kdg.airfreight.flight.domain.flight.replanning.Replanning;
import be.kdg.airfreight.flight.domain.flight.replanning.ReplanningRefusedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
class ErrorHandling {
    static final String DATABASE_FAILURE =
            "The action was not saved because of a database problem. Try again later.";

    // only our own input exceptions so any other bug stays a 500
    @ExceptionHandler({
        InvalidDelayException.class,
        InvalidScheduleException.class,
        InvalidActualTimeException.class
    })
    ProblemDetail invalidInput(RuntimeException exception, HttpServletRequest request) {
        return refused(HttpStatus.BAD_REQUEST, exception, request);
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception, HttpServletRequest request) {
        log.warn(
                "Not found on {} {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage());
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DomainRuleViolationException.class)
    ProblemDetail domainRuleViolated(
            DomainRuleViolationException exception, HttpServletRequest request) {
        return refused(HttpStatus.CONFLICT, exception, request);
    }

    // more specific than the handler above so spring picks this one, it adds the refusal code
    // and for TOO_LATE the limit, which the page shows in brussels time
    @ExceptionHandler(ActualTimeRefusedException.class)
    ProblemDetail actualTimeRefused(
            ActualTimeRefusedException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = refused(HttpStatus.CONFLICT, exception, request);
        problemDetail.setProperty("refusal", exception.refusal().name());
        if (exception.latestAllowed() != null) {
            problemDetail.setProperty("latestAllowed", exception.latestAllowed().toString());
        }
        return problemDetail;
    }

    // more specific than the handler above so spring picks this one
    // the extra properties let the flight page offer replanning
    @ExceptionHandler(TotalDelayExceededException.class)
    ProblemDetail totalDelayExceeded(
            TotalDelayExceededException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = refused(HttpStatus.CONFLICT, exception, request);
        problemDetail.setProperty("refusal", "TOTAL_DELAY_EXCEEDED");
        problemDetail.setProperty("totalMinutes", exception.totalMinutes());
        problemDetail.setProperty("maxTotalMinutes", Flight.MAX_TOTAL_DELAY_MINUTES);
        problemDetail.setProperty("replanningPossible", exception.replanningPossible());
        // the proposal the dialog shows, computed the same way as the replan itself
        Replanning proposal = exception.proposal();
        if (proposal != null) {
            problemDetail.setProperty("newFlightNumber", proposal.newFlightNumber());
            problemDetail.setProperty("newScheduledDeparture", proposal.newScheduledDeparture());
            problemDetail.setProperty("newScheduledArrival", proposal.newScheduledArrival());
        }
        return problemDetail;
    }

    @ExceptionHandler(ReplanningRefusedException.class)
    ProblemDetail replanningRefused(
            ReplanningRefusedException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = refused(HttpStatus.CONFLICT, exception, request);
        problemDetail.setProperty("refusal", exception.refusal().name());
        return problemDetail;
    }

    // a database outage stays a 500, but the page can now say what happened
    // only outages, so a constraint violation stays a bug like any other 500
    @ExceptionHandler({
        DataAccessResourceFailureException.class,
        TransientDataAccessException.class,
        RecoverableDataAccessException.class,
        TransactionException.class
    })
    ProblemDetail databaseFailure(RuntimeException exception, HttpServletRequest request) {
        log.error(
                "Database failure on {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, DATABASE_FAILURE);
    }

    // the path is logged too because not every refusal text names the flight
    private static ProblemDetail refused(
            HttpStatus status, RuntimeException exception, HttpServletRequest request) {
        log.info(
                "Refused {} {} with {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                status.value(),
                exception.getMessage());
        return problem(status, exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatusCode status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
