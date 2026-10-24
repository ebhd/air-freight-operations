package be.kdg.airfreight.flight.api;

import be.kdg.airfreight.flight.domain.DomainRuleViolationException;
import be.kdg.airfreight.flight.domain.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
class ErrorHandling {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidInput(IllegalArgumentException exception) {
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

    private static ProblemDetail problem(HttpStatusCode status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setDetail(detail);
        return problemDetail;
    }
}
