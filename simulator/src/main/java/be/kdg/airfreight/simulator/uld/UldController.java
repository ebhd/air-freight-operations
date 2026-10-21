package be.kdg.airfreight.simulator.uld;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/simulator/uld-bookings")
class UldController {
    private final UldPublisher publisher;

    UldController(UldPublisher publisher) {
        this.publisher = publisher;
    }

    // 202 because cargo decides later whether the booking is accepted
    @PostMapping
    ResponseEntity<Submission> submit(@RequestBody List<UldRequest> bookings) {
        requireScheduledDepartureDates(bookings);
        List<String> messageIds = new ArrayList<>();
        for (UldRequest booking : bookings) {
            messageIds.add(publisher.publish(booking));
        }
        return ResponseEntity.accepted().body(new Submission(List.copyOf(messageIds)));
    }

    // check every record first so a 400 means nothing was published
    private static void requireScheduledDepartureDates(List<UldRequest> bookings) {
        if (bookings.isEmpty()) {
            throw new InvalidUldRequestException("Send at least one ULD record");
        }
        for (int index = 0; index < bookings.size(); index++) {
            UldRequest booking = bookings.get(index);
            if (booking == null || booking.scheduledDepartureDate() == null) {
                throw new InvalidUldRequestException(
                        "Record "
                                + (index + 1)
                                + ": scheduledDepartureDate is required (yyyy-MM-dd, UTC)");
            }
        }
    }

    @ExceptionHandler(InvalidUldRequestException.class)
    ProblemDetail invalidRequest(InvalidUldRequestException exception) {
        log.warn("Refused ULD simulator request: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    // the default spring 400 does not say which value was wrong
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableRequest(HttpMessageNotReadableException exception) {
        String cause = exception.getMostSpecificCause().getMessage();
        log.warn("Refused unreadable ULD simulator request: {}", cause);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Body must be a JSON array of ULD records with dates as yyyy-MM-dd (UTC): "
                        + cause);
    }

    @ExceptionHandler(AmqpException.class)
    ProblemDetail rabbitMqUnavailable(AmqpException exception) {
        log.error("RabbitMQ unavailable while publishing ULD records", exception);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "RabbitMQ is unavailable; records before the failure may already be published"
                        + " (see the simulator log). Resending the whole request is safe: Cargo"
                        + " ignores a ULD code already booked on that flight.");
    }

    record Submission(List<String> messageIds) {}
}
