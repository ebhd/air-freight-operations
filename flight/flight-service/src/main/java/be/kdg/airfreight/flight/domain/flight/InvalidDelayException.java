package be.kdg.airfreight.flight.domain.flight;

public class InvalidDelayException extends RuntimeException {
    public InvalidDelayException(String message) {
        super(message);
    }
}
