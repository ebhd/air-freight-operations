package be.kdg.airfreight.flight.domain;

public class InvalidScheduleException extends IllegalArgumentException {
    public InvalidScheduleException(String message) {
        super(message);
    }
}
