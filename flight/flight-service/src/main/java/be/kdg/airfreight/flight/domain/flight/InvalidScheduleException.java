package be.kdg.airfreight.flight.domain.flight;

public class InvalidScheduleException extends IllegalArgumentException {
    public InvalidScheduleException(String message) {
        super(message);
    }
}
