package be.kdg.airfreight.flight.api;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;
import be.kdg.airfreight.flight.domain.NotFoundException;
import be.kdg.airfreight.flight.domain.flight.ActualTimeRefusedException;
import be.kdg.airfreight.flight.domain.flight.Flight;
import be.kdg.airfreight.flight.domain.flight.InvalidActualTimeException;
import be.kdg.airfreight.flight.domain.flight.InvalidDelayException;
import be.kdg.airfreight.flight.domain.flight.InvalidScheduleException;
import be.kdg.airfreight.flight.domain.flight.TotalDelayExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
class ErrorHandling {
    // only our own input exceptions so any other bug stays a 500
    @ExceptionHandler({
        InvalidDelayException.class,
        InvalidScheduleException.class,
        InvalidActualTimeException.class
    })
    ProblemDetail invalidInput(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DomainRuleViolationException.class)
    ProblemDetail domainRuleViolated(DomainRuleViolationException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    // more specific than the handler above so spring picks this one, it adds the refusal code
    // and for TOO_LATE the limit, which the page shows in brussels time
    @ExceptionHandler(ActualTimeRefusedException.class)
    ProblemDetail actualTimeRefused(ActualTimeRefusedException exception) {
        ProblemDetail problemDetail = problem(HttpStatus.CONFLICT, exception.getMessage());
        problemDetail.setProperty("refusal", exception.refusal().name());
        if (exception.latestAllowed() != null) {
            problemDetail.setProperty("latestAllowed", exception.latestAllowed().toString());
        }
        return problemDetail;
    }

    // more specific than the handler above so spring picks this one
    // the extra properties let the flight page offer replanning
    @ExceptionHandler(TotalDelayExceededException.class)
    ProblemDetail totalDelayExceeded(TotalDelayExceededException exception) {
        ProblemDetail problemDetail = problem(HttpStatus.CONFLICT, exception.getMessage());
        problemDetail.setProperty("refusal", "TOTAL_DELAY_EXCEEDED");
        problemDetail.setProperty("totalMinutes", exception.totalMinutes());
        problemDetail.setProperty("maxTotalMinutes", Flight.MAX_TOTAL_DELAY_MINUTES);
        problemDetail.setProperty("replanningPossible", exception.replanningPossible());
        return problemDetail;
    }

    private static ProblemDetail problem(HttpStatusCode status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setDetail(detail);
        return problemDetail;
    }
}
