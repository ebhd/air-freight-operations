package be.kdg.airfreight.flight.domain.flight.actualtime;

// the request itself is wrong, a missing time, so the api answers 400 like InvalidDelayException
public class InvalidActualTimeException extends RuntimeException {
    public InvalidActualTimeException(String message) {
        super(message);
    }
}
