package be.kdg.airfreight.cargo.domain.exception;

// a missing or invalid input value so reject it and never retry
public class InvalidCargoValueException extends RuntimeException {
    public InvalidCargoValueException(String message) {
        super(message);
    }
}
